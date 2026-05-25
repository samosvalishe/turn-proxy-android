package com.freeturn.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.DnsMode
import com.freeturn.app.data.TunnelRoute
import com.freeturn.app.data.TunnelTransport
import com.freeturn.app.domain.WireGuardTunnelManager
import com.freeturn.app.domain.XrayTunnelManager
import com.freeturn.app.domain.server.KCP_FEC_VALUE
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

sealed class StartupResult {
    data object Success : StartupResult()
    data class Failed(val message: String) : StartupResult()
}

class ProxyService : Service() {

    companion object {
        const val MAX_RESTARTS = 8
        private const val CHANNEL_PROXY = "ProxyChannel"
        private const val CHANNEL_CAPTCHA = "CaptchaChannel"
        private const val NOTIF_ID_FG = 1
        private const val NOTIF_ID_CAPTCHA = 2
        // Жёстко привязываемся к строке-объявлению капчи в бинарнике, чтобы
        // случайные localhost-URL в других логах не открывали диалог.
        // Новое ядро (manual_captcha.go) пишет "manually open this URL: <url>".
        // Старое — "Open this URL in your browser: <url>". Поддерживаем оба, чтобы
        // приложение работало с кастомным ядром, оставшимся у пользователя.
        private val CAPTCHA_URL_REGEX =
            Pattern.compile("""(?:manually open this URL|Open this URL in your browser):\s*(https?://\S+)""")

        // События жизненного цикла соединений, публикуемые ядром (client/main.go).
        // Не-VLESS: несколько потоков со своим [STREAM N], у каждого свой Established/Closed.
        private val STREAM_ESTABLISHED_REGEX =
            Pattern.compile("""\[STREAM (\d+)\] Established DTLS connection""")
        private val STREAM_CLOSED_REGEX =
            Pattern.compile("""\[STREAM (\d+)\] Closed DTLS connection""")
        // VLESS: ядро само пишет агрегированное число активных сессий.
        private val VLESS_ACTIVE_REGEX =
            Pattern.compile("""\[session \d+\] (?:connected|disconnected) \(active: (\d+)\)""")
        // VLESS: целевое число сессий приходит в этой строке до первого connected.
        private val VLESS_TOTAL_REGEX =
            Pattern.compile("""VLESS mode: waiting for sessions to connect \(total: (\d+)\)""")
        private const val WIREGUARD_START_DELAY_MS = 2_000L
        private const val NETWORK_CALLBACK_WARMUP_MS = 3_000L
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var openAppIntent: PendingIntent? = null

    private val process = AtomicReference<Process?>(null)
    private val userStopped = AtomicBoolean(false)
    private val sessionKillScheduled = AtomicBoolean(false)

    private val handler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkInitialized = false
    @Volatile private var networkDebounceJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastPhysicalNetworkKey: String? = null
    private val restartCount = AtomicInteger(0)
    @Volatile private var captchaNotificationActive = false

    private lateinit var prefs: AppPreferences
    private lateinit var serviceScope: CoroutineScope
    private lateinit var wireGuard: WireGuardTunnelManager
    private lateinit var xray: XrayTunnelManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        wireGuard = WireGuardTunnelManager(applicationContext)
        xray = XrayTunnelManager(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROXY,
                    getString(R.string.notif_channel_proxy),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CAPTCHA,
                    getString(R.string.notif_channel_captcha),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ВАЖНО: startForeground вызываем ПЕРВЫМ делом и БЕЗУСЛОВНО. Если ранее
        // была ветка с return до startForeground (например, при stale
        // ProxyServiceState.isRunning после kill'а сервиса без onDestroy),
        // система через ~5с бросала ForegroundServiceDidNotStartInTimeException.
        if (openAppIntent == null) {
            openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_PROXY)
            .setContentTitle(getString(R.string.notif_proxy_title))
            .setContentText(getString(R.string.notif_proxy_connecting))
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
        startForeground(NOTIF_ID_FG, notification)

        // Если процесс ядра ещё жив — это повторный onStartCommand (например,
        // sticky-рестарт). Не запускаем второй процесс, но требование о
        // startForeground выше уже выполнено.
        if (process.get() != null) {
            ProxyServiceState.setRunning(true)
            return START_STICKY
        }

        ProxyServiceState.setRunning(true)
        userStopped.set(false)
        restartCount.set(0)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VkTurn::BgLock")
        wakeLock?.acquire(TimeUnit.HOURS.toMillis(24))

        registerNetworkCallback()

        ProxyServiceState.addLog("=== ЗАПУСК ПРОКСИ ===")
        serviceScope.launch { startBinaryProcess() }

        return START_STICKY
    }

    private suspend fun startBinaryProcess() {
        if (userStopped.get()) return

        val cfg = prefs.clientConfigFlow.first()
        ProxyServiceState.setLogsEnabled(cfg.logsEnabled)
        // Wrap-обфускация и VLESS bonding управляются на серверном экране, но
        // должны передаваться и клиенту с тем же ключом, иначе DTLS-handshake
        // не сойдётся. Источник истины — общий serverOpts.
        val srv = prefs.serverOptsFlow.first()

        val executable = "${applicationInfo.nativeLibraryDir}/libvkturn.so"

        val cmdArgs = mutableListOf<String>()

        if (cfg.isRawMode) {
            val parts = cfg.rawCommand.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            cmdArgs.add(executable)
            cmdArgs.addAll(parts.drop(1))
        } else {
            cmdArgs.add(executable)
            cmdArgs.add("-peer"); cmdArgs.add(cfg.serverAddress)

            cmdArgs.add(if (cfg.vkLink.contains("yandex")) "-yandex-link" else "-vk-link")
            cmdArgs.add(cfg.vkLink)
            cmdArgs.add("-listen"); cmdArgs.add(cfg.localPort)
            if (cfg.threads > 0) { cmdArgs.add("-n"); cmdArgs.add(cfg.threads.toString()) }
            // -streams-per-cred передаём только если пользователь поменял дефолт.
            if (cfg.streamsPerCred > 0 && cfg.streamsPerCred != 10) {
                cmdArgs.add("-streams-per-cred"); cmdArgs.add(cfg.streamsPerCred.toString())
            }
            if (cfg.vlessMode) cmdArgs.add("-vless")
            else if (cfg.useUdp) cmdArgs.add("-udp")
            // VLESS bonding имеет смысл только в VLESS-режиме.
            if (cfg.vlessMode && srv.vlessBond) cmdArgs.add("-vless-bond")
            // WRAP: тот же ключ, что и у сервера (хранится в EncryptedSharedPreferences).
            // Без 64-hex ключа флаг не передаём — ядро упадёт.
            if (srv.wrapEnabled &&
                srv.wrapKey.length == 64 &&
                srv.wrapKey.matches(Regex("^[0-9a-fA-F]+$"))
            ) {
                cmdArgs.add("-wrap")
                cmdArgs.add("-wrap-key"); cmdArgs.add(srv.wrapKey)
            }
            if (cfg.manualCaptcha) cmdArgs.add("--manual-captcha")

            if (cfg.debugMode) cmdArgs.add("-debug")
            if (cfg.useCarrierDns) {
                val dns = activeNetworkDnsServers()
                if (dns.isNotBlank()) {
                    cmdArgs.add("-dns-servers"); cmdArgs.add(dns)
                }
            }
            if (cfg.dnsMode == DnsMode.UDP || cfg.dnsMode == DnsMode.DOH) {
                cmdArgs.add("-dns"); cmdArgs.add(cfg.dnsMode)
            }
            if (cfg.forcePort443) { cmdArgs.add("-port"); cmdArgs.add("443") }
            // Альтернативный TURN-узел: переключает клиент на указанный server-side relay
            // вместо автоподбора. Адрес задаётся пользователем, флаг работает только при
            // непустом значении (иначе ядро запустится без -turn и будет автоподбор).
            if (cfg.magicSwitch) {
                val turn = cfg.magicTurn.trim()
                if (turn.isNotEmpty()) {
                    cmdArgs.add("-turn"); cmdArgs.add(turn)
                }
            }
        }

        var exitCode = -1
        val startedAt = System.currentTimeMillis()
        var startupEmitted = false
        var startupFailed = false
        var wireGuardStarted = false
        var xrayStarted = false
        var captchaSessionCounter = 0L

        // --- Трекинг активных соединений для индикации состояния в UI. ---
        // Не-VLESS: каждый поток логирует свой [STREAM N] Established/Closed парой
        // (defer Closed ставится ДО логирования Established, см. client/main.go).
        // Считаем именно инкрементами, а не множеством уникальных ID: в ядре есть
        // особенность — первый поток запускается с id=1 и цикл снова итерируется
        // с i=1, из-за чего один streamID дублируется при -n N. Для счётчика это
        // безопасно (на два Established придёт два Closed), для Set это давало
        // бы заниженное число активных (N-1 вместо N).
        var nonVlessActive = 0
        // Для не-VLESS целевое число потоков известно из конфига (-n). Если threads == 0,
        // ядро запускает один поток, считаем total = 1.
        val nonVlessTotal = if (cfg.isRawMode) 0 else if (cfg.threads > 0) cfg.threads else 1
        var vlessActive = 0
        var vlessTotal = 0
        var isVless = cfg.vlessMode

        fun publishStats() {
            val stats = if (isVless) {
                ConnectionStats(vlessActive, vlessTotal)
            } else {
                ConnectionStats(nonVlessActive, nonVlessTotal)
            }
            ProxyServiceState.setConnectionStats(stats)
        }
        // Сброс на старте сессии (в том числе на watchdog-рестарте).
        publishStats()
        try {
            ProxyServiceState.addLog("Команда: ${cmdArgs.joinToString(" ")}")

            val proc = withContext(Dispatchers.IO) {
                val pb = ProcessBuilder(cmdArgs).redirectErrorStream(true)
                // Ядро по умолчанию пишет vk_profile.json в CWD (= /data/app/.../lib/<abi>/),
                // а это read-only mount на Android. Перенаправляем в filesDir, где записать
                // можно. См. client/profiles.go: profileFilePath() читает $VK_PROFILE_PATH
                // в первую очередь.
                pb.environment()["VK_PROFILE_PATH"] =
                    File(filesDir, "vk_profile.json").absolutePath
                // KCP FEC — должно совпадать с сервером. Включается из ServerOpts.
                if (srv.kcpFec) {
                    pb.environment()["VK_TURN_KCP_FEC"] = KCP_FEC_VALUE
                }
                // CWD тоже подменяем на writeable dir — на случай если Go-код или его
                // зависимости пишут что-то относительными путями (логи кеша tls-client и т.п.).
                pb.directory(filesDir)
                pb.start()
            }
            process.set(proc)

            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                while (true) {
                    line = try {
                        reader.readLine()
                    } catch (e: java.io.IOException) {
                        // При destroyCompat()/Process.destroy() с другого треда
                        // нативный pipe закрывается, и блокирующий readLine() бросает
                        // IOException("Stream closed" / "read interrupted by close()").
                        // Это нормальный путь остановки — выходим из цикла молча.
                        val msg = e.message.orEmpty()
                        val benign = userStopped.get() ||
                            msg.contains("interrupted by close", ignoreCase = true) ||
                            msg.contains("Stream closed", ignoreCase = true) ||
                            msg.contains("Bad file descriptor", ignoreCase = true)
                        if (!benign) {
                            ProxyServiceState.addLog("Чтение лога ядра прервано: ${e.message}")
                        }
                        null
                    }
                    if (line == null) break
                    val l = line ?: continue
                    ProxyServiceState.addLog(l)

                    // Детекция URL ручной капчи. Каждый раз выдаём новый sessionId,
                    // чтобы диалог пересоздавал WebView, даже если URL не поменялся
                    // (бинарник всегда использует http://localhost:8765).
                    val captchaMatcher = CAPTCHA_URL_REGEX.matcher(l)
                    if (captchaMatcher.find()) {
                        val url = captchaMatcher.group(1)!!
                        captchaSessionCounter += 1
                        ProxyServiceState.setCaptchaSession(
                            CaptchaSession(url, captchaSessionCounter)
                        )
                        // Показываем нотификацию только если предыдущая капча уже закрыта.
                        // Бинарник может выдать несколько URL подряд за одну авторизацию —
                        // не плодим спам.
                        if (!captchaNotificationActive) {
                            showCaptchaNotification()
                            captchaNotificationActive = true
                        }
                    }

                    // Капча-сессия закончилась: бинарник либо завершил auth-чейн
                    // (Failed/Success), либо сама капча провалилась (timeout). Закрываем
                    // диалог — следующая капча-сессия откроет его заново через новый sessionId.
                    if (ProxyServiceState.captchaSession.value != null && (
                            l.contains("[VK Auth] Failed") ||
                            l.contains("[VK Auth] Success") ||
                            (l.contains("[Captcha]") && l.contains("failed"))
                        )) {
                        ProxyServiceState.setCaptchaSession(null)
                        cancelCaptchaNotification()
                    }

                    // Парсинг событий жизненного цикла соединений. Обновляем stats
                    // и используем первое "реально подключилось" как сигнал успешного старта.
                    var statsChanged = false
                    STREAM_ESTABLISHED_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            nonVlessActive += 1
                            statsChanged = true
                            isVless = false
                        }
                    }
                    STREAM_CLOSED_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            if (nonVlessActive > 0) nonVlessActive -= 1
                            statsChanged = true
                        }
                    }
                    VLESS_TOTAL_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            vlessTotal = m.group(1)!!.toInt()
                            isVless = true
                            statsChanged = true
                        }
                    }
                    VLESS_ACTIVE_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            vlessActive = m.group(1)!!.toInt()
                            isVless = true
                            statsChanged = true
                        }
                    }
                    if (statsChanged) publishStats()

                    // Startup: ядро упало с panic/fatal/окончательно не смогло
                    // получить creds ДО того, как удалось подключиться — считаем
                    // запуск неудачным. Первая строка без этих маркеров больше не
                    // трактуется как Success (ядро могло написать "Connecting..."
                    // и только потом упасть).
                    //
                    // ВАЖНО: не матчим подстроку "rate limit" — Go-сторона выводит
                    // её в кулдаун-логах ("identity cooldown", "VK throttle ...
                    // trying next") как рабочую часть retry-цикла, не как ошибку.
                    // Финальная неудача — "all VK credentials failed".
                    if (!startupEmitted) {
                        val lower = l.lowercase()
                        val hasFatal = lower.startsWith("panic:") ||
                            lower.startsWith("fatal error:") ||
                            lower.contains("all vk credentials failed") ||
                            lower.contains("fatal_captcha")
                        val hasConnection = (isVless && vlessActive > 0) ||
                            (!isVless && nonVlessActive > 0)
                        when {
                            hasFatal -> {
                                ProxyServiceState.setStartupResult(StartupResult.Failed(l))
                                updateNotification("Ошибка подключения")
                                startupFailed = true
                                startupEmitted = true
                            }
                            hasConnection -> {
                                try {
                                    if (cfg.tunnelTransport in TunnelTransport.ALL) {
                                        ProxyServiceState.addLog(
                                            "Туннельное приложение: ждём ${WIREGUARD_START_DELAY_MS}мс после поднятия TURN-туннеля"
                                        )
                                        kotlinx.coroutines.delay(WIREGUARD_START_DELAY_MS)
                                        if (userStopped.get() || process.get() !== proc) {
                                            ProxyServiceState.addLog(
                                                "Туннельное приложение: старт отменён, прокси уже останавливается"
                                            )
                                            break
                                        }
                                    }
                                    wireGuard.startAfterProxyReady(cfg)
                                    wireGuardStarted = cfg.tunnelTransport == TunnelTransport.WIREGUARD
                                    xray.startAfterProxyReady(cfg)
                                    xrayStarted = cfg.tunnelTransport == TunnelTransport.VLESS
                                    ProxyServiceState.setStartupResult(StartupResult.Success)
                                    ProxyServiceState.markConnectedIfAbsent(SystemClock.elapsedRealtime())
                                    updateNotification(
                                        when {
                                            wireGuardStarted && xrayStarted -> "Прокси, WireGuard и Xray активны"
                                            wireGuardStarted -> "Прокси и WireGuard активны"
                                            xrayStarted -> "Прокси и Xray активны"
                                            else -> "Прокси активен"
                                        }
                                    )
                                } catch (e: Exception) {
                                    val message = e.message ?: e.javaClass.simpleName
                                    ProxyServiceState.addLog("Туннельное приложение: ошибка запуска: $message")
                                    ProxyServiceState.setStartupResult(
                                        StartupResult.Failed("Туннельное приложение не запустилось: $message")
                                    )
                                    updateNotification("Ошибка туннельного приложения")
                                    startupFailed = true
                                    proc.destroyCompat()
                                }
                                startupEmitted = true
                            }
                        }
                    }

                    // compareAndSet гарантирует единственный postDelayed даже при параллельных quota-ошибках
                    if (isQuotaError(l) && sessionKillScheduled.compareAndSet(false, true)) {
                        ProxyServiceState.addLog(">>> QUOTA ERROR — сброс сессии через 2с")
                        handler.postDelayed({
                            sessionKillScheduled.set(false)
                            if (!userStopped.get()) {
                                restartCount.set(0)
                                process.get()?.destroyCompat()
                            }
                        }, 2_000)
                    }
                }
            }

            exitCode = if (withContext(Dispatchers.IO) {
                    proc.waitForCompat(5, TimeUnit.MINUTES)
                }) proc.exitValue() else -1
            ProxyServiceState.addLog("=== ПРОЦЕСС ОСТАНОВЛЕН (Код: $exitCode) ===")
            if (!startupEmitted) {
                ProxyServiceState.setStartupResult(StartupResult.Failed(
                    "Процесс завершился без вывода (код: $exitCode)"))
            }

        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("error=13") || msg.contains("Permission denied")) {
                ProxyServiceState.addLog("КРИТИЧЕСКАЯ ОШИБКА: Отказано в запуске ядра — ваше устройство блокирует выполнение файлов из внутреннего хранилища (SELinux/noexec). Используйте встроенное ядро.")
                ProxyServiceState.setStartupResult(StartupResult.Failed(msg))
                startupFailed = true
            } else {
                ProxyServiceState.addLog("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
            }
        } finally {
            ProxyServiceState.setCaptchaSession(null)
            cancelCaptchaNotification()
            xray.stop()
            wireGuard.stop()
            // Процесс мёртв — активных соединений нет. При watchdog-рестарте
            // publishStats на новом старте снова выставит правильный target.
            ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
            process.set(null)
            when {
                userStopped.get() -> {
                    ProxyServiceState.setRunning(false)
                    stopSelf()
                }
                startupFailed -> {
                    ProxyServiceState.addLog("=== Ошибка при запуске, watchdog не активирован ===")
                    ProxyServiceState.setRunning(false)
                    // Убираем proxyFailed.tryEmit, так как startProxy и так обработает StartupResult.Failed
                    stopSelf()
                }
                exitCode == 0 -> {
                    val uptime = System.currentTimeMillis() - startedAt
                    if (uptime < 5_000L) {
                        ProxyServiceState.addLog("=== Быстрый выход (${uptime}мс) — проверьте VK-ссылку и настройки ===")
                    } else {
                        ProxyServiceState.addLog("=== Сессия завершена нормально ===")
                    }
                    ProxyServiceState.setRunning(false)
                    stopSelf()
                }
                else -> scheduleWatchdogRestart()
            }
        }
    }

    // Watchdog

    private fun scheduleWatchdogRestart() {
        val count = restartCount.incrementAndGet()
        if (count > MAX_RESTARTS) {
            ProxyServiceState.addLog("=== WATCHDOG: превышен лимит попыток ($MAX_RESTARTS), остановка ===")
            ProxyServiceState.setRunning(false)
            ProxyServiceState.emitFailed()
            stopSelf()
            return
        }
        val baseDelay = minOf(1_000L * count, 30_000L)
        val jitter = Random.nextLong(0, 500)
        val delay = baseDelay + jitter
        ProxyServiceState.addLog("=== WATCHDOG: перезапуск через ${delay}мс (попытка $count/$MAX_RESTARTS) ===")
        updateNotification("Переподключение ($count/$MAX_RESTARTS)...")
        handler.postDelayed({
            if (!userStopped.get()) serviceScope.launch { startBinaryProcess() }
        }, delay)
    }

    // Network handover

    private fun registerNetworkCallback() {
        networkInitialized = false
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val registeredAt = SystemClock.elapsedRealtime()
        lastPhysicalNetworkKey = physicalNetworkKey(cm)
        networkInitialized = true

        fun schedulePhysicalNetworkCheck(reason: String) {
            if (SystemClock.elapsedRealtime() - registeredAt < NETWORK_CALLBACK_WARMUP_MS) {
                ProxyServiceState.addLog("=== СЕТЬ: начальное событие проигнорировано ($reason) ===")
                return
            }

            networkDebounceJob?.cancel()
            networkDebounceJob = serviceScope.launch {
                kotlinx.coroutines.delay(2_000)
                val oldKey = lastPhysicalNetworkKey
                val newKey = physicalNetworkKey(cm)
                if (oldKey == newKey) {
                    ProxyServiceState.addLog("=== СЕТЬ: физическая сеть не изменилась ($reason) ===")
                    return@launch
                }

                lastPhysicalNetworkKey = newKey
                if (newKey == null) {
                    ProxyServiceState.addLog("=== СЕТЬ: физическая сеть недоступна ($reason) ===")
                    return@launch
                }

                if (!userStopped.get() && process.get() != null) {
                    ProxyServiceState.addLog("=== СМЕНА СЕТИ — ПЕРЕЗАПУСК ===")
                    updateNotification("Смена сети, переподключение...")
                    restartCount.set(0)
                    process.get()?.destroyCompat()
                }
            }
        }

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    ProxyServiceState.addLog("=== СЕТЬ: VPN-событие проигнорировано ===")
                    return
                }

                if (!networkInitialized) {
                    networkInitialized = true
                    lastPhysicalNetworkKey = physicalNetworkKey(cm)
                    return
                }

                schedulePhysicalNetworkCheck("available")
            }

            override fun onLost(network: Network) {
                schedulePhysicalNetworkCheck("lost")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    return
                }
                schedulePhysicalNetworkCheck("capabilities")
            }
        }
        networkCallback = cb
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(request, cb)
    }

    private fun physicalNetworkKey(cm: ConnectivityManager): String? {
        return cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                return@mapNotNull null
            }
            val transports = buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
            }
            if (transports.isEmpty()) return@mapNotNull null
            val lp = cm.getLinkProperties(network)
            val iface = lp?.interfaceName.orEmpty()
            val addresses = lp?.linkAddresses
                ?.map { it.address.hostAddress.orEmpty() }
                ?.filter { it.isNotBlank() }
                ?.sorted()
                ?.joinToString(",")
                .orEmpty()
            "${transports.joinToString("+")}|$iface|$addresses"
        }.sorted().joinToString(";").ifBlank { null }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    // Notification

    private fun showCaptchaNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_CAPTCHA)
            .setContentTitle(getString(R.string.notif_captcha_title))
            .setContentText(getString(R.string.notif_captcha_text))
            .setSmallIcon(R.drawable.ic_notification_captcha)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_CAPTCHA, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS отозван юзером на API 33+ — молча игнорируем,
            // диалог в UI всё равно откроется через captchaSession StateFlow.
        }
    }

    private fun cancelCaptchaNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_CAPTCHA)
        captchaNotificationActive = false
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_PROXY)
            .setContentTitle(getString(R.string.notif_proxy_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_FG, notification)
        } catch (_: SecurityException) {}
    }

    // Helpers

    private fun isQuotaError(line: String): Boolean =
        line.lowercase().contains("quota")

    /**
     * DNS активной сети (оператор/Wi-Fi). Возвращает comma-separated список IP,
     * пригодный для флага `-dns-servers` ядра. Пусто, если сеть недоступна или
     * у linkProperties нет DNS (что норма на эмуляторе/некоторых VPN).
     */
    private fun activeNetworkDnsServers(): String {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return ""
            val lp = cm.getLinkProperties(net) ?: return ""
            lp.dnsServers
                .mapNotNull { it.hostAddress }
                .filter { it.isNotBlank() }
                .joinToString(",")
        } catch (_: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userStopped.set(true)
        ProxyServiceState.setRunning(false)
        ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
        ProxyServiceState.clearConnectedSince()
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()
        cancelCaptchaNotification()
        ProxyServiceState.addLog("=== ОСТАНОВКА ИЗ ИНТЕРФЕЙСА ===")
        process.get()?.destroyCompat()
        runBlocking {
            xray.stop()
            wireGuard.stop()
        }
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}

private fun Process.destroyCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) destroyForcibly() else destroy()
}

private fun Process.waitForCompat(timeout: Long, unit: TimeUnit): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return waitFor(timeout, unit)
    val deadline = System.currentTimeMillis() + unit.toMillis(timeout)
    while (System.currentTimeMillis() < deadline) {
        try { exitValue(); return true } catch (_: IllegalThreadStateException) { Thread.sleep(100) }
    }
    return try { exitValue(); true } catch (_: IllegalThreadStateException) { false }
}
