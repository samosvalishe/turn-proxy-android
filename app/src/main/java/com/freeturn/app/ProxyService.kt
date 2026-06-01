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
import com.freeturn.app.data.Provider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        // UDP-релей: несколько потоков со своим [STREAM N], у каждого свой Established/Closed.
        private val STREAM_ESTABLISHED_REGEX =
            Pattern.compile("""\[STREAM (\d+)\] Established DTLS connection""")
        private val STREAM_CLOSED_REGEX =
            Pattern.compile("""\[STREAM (\d+)\] Closed DTLS connection""")
        // TCP-режим (-mode tcp): ядро само пишет агрегированное число активных сессий.
        private val TCP_ACTIVE_REGEX =
            Pattern.compile("""\[session \d+\] (?:connected|disconnected) \(active: (\d+)\)""")
        // TCP-режим: целевое число сессий приходит в этой строке до первого connected.
        // Новое ядро (tcpfwd.go): "TCP mode: waiting for sessions to connect (total: N)...".
        private val TCP_TOTAL_REGEX =
            Pattern.compile("""TCP mode: waiting for sessions to connect \(total: (\d+)\)""")
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var openAppIntent: PendingIntent? = null

    private var currentBaseStatus = ""
    private var currentSpeedText = ""
    @Volatile private var speedLoopJob: kotlinx.coroutines.Job? = null

    private val process = AtomicReference<Process?>(null)
    private val userStopped = AtomicBoolean(false)
    private val sessionKillScheduled = AtomicBoolean(false)

    private val handler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkInitialized = false
    @Volatile private var networkDebounceJob: kotlinx.coroutines.Job? = null
    private val restartCount = AtomicInteger(0)
    @Volatile private var captchaNotificationActive = false

    private lateinit var prefs: AppPreferences
    private lateinit var serviceScope: CoroutineScope

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
        currentBaseStatus = getString(R.string.notif_proxy_connecting)
        startForeground(NOTIF_ID_FG, createNotification())

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
        startSpeedMonitor()
        serviceScope.launch { startBinaryProcess() }

        return START_STICKY
    }

    private suspend fun startBinaryProcess() {
        if (userStopped.get()) return

        val cfg = prefs.clientConfigFlow.first()
        // Obf-обфускация управляется на серверном экране, но должна передаваться
        // и клиенту с тем же ключом, иначе DTLS-handshake не сойдётся. Источник
        // истины — общий serverOpts.
        val srv = prefs.serverOptsFlow.first()

        // Имя ядра версионируется (libfreeturn-<ver>-android-arm64.so) и меняется
        // между релизами — не хардкодим. Ищем в nativeLibraryDir libfreeturn*.so
        // (Android извлекает туда только файлы вида lib*.so). При нескольких версиях
        // берём лексикографически старшую.
        val libDir = File(applicationInfo.nativeLibraryDir)
        val executable = libDir.listFiles { f ->
            f.name.startsWith("libfreeturn") && f.name.endsWith(".so")
        }?.maxByOrNull { it.name }?.absolutePath

        if (executable == null) {
            ProxyServiceState.addLog(
                "КРИТИЧЕСКАЯ ОШИБКА: ядро libfreeturn*.so не найдено в ${libDir.path}. " +
                "Положите бинарник в jniLibs/arm64-v8a/ (имя обязано начинаться с lib и оканчиваться на .so)."
            )
            ProxyServiceState.setStartupResult(StartupResult.Failed("core binary not found"))
            ProxyServiceState.setRunning(false)
            stopSelf()
            return
        }

        val cmdArgs = mutableListOf<String>()

        if (cfg.isRawMode) {
            val parts = cfg.rawCommand.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            cmdArgs.add(executable)
            cmdArgs.addAll(parts.drop(1))
        } else {
            cmdArgs.add(executable)
            cmdArgs.add("-peer"); cmdArgs.add(cfg.serverAddress)

            cmdArgs.add("-provider"); cmdArgs.add(cfg.provider)
            // -link нужен только провайдеру vk (callroom URL).
            if (cfg.provider == Provider.VK) { cmdArgs.add("-link"); cmdArgs.add(cfg.vkLink) }
            cmdArgs.add("-listen"); cmdArgs.add(cfg.localPort)
            if (cfg.threads > 0) { cmdArgs.add("-n"); cmdArgs.add(cfg.threads.toString()) }
            // -streams-per-cred передаём только если пользователь поменял дефолт.
            if (cfg.streamsPerCred > 0 && cfg.streamsPerCred != 10) {
                cmdArgs.add("-streams-per-cred"); cmdArgs.add(cfg.streamsPerCred.toString())
            }
            // Режим туннеля: tcp-форвард (Xray/sing-box) vs udp-релей (WireGuard, дефолт).
            if (cfg.tcpForward) { cmdArgs.add("-mode"); cmdArgs.add("tcp") }
            // Bond работает только в tcp-режиме; в новом ядре это client-only флаг
            // (сервер сам детектит bond по magic-префиксу).
            if (cfg.tcpForward && cfg.bond) cmdArgs.add("-bond")
            // TURN-транспорт: UDP вместо TCP/TLS по умолчанию.
            if (cfg.useUdp) { cmdArgs.add("-transport"); cmdArgs.add("udp") }
            // Обфускация (-obf-profile): профиль и ключ должны совпадать с сервером
            // (хранятся в общем serverOpts). Без валидного 64-hex не передаём —
            // ядро упадёт на DecodeKey.
            if (srv.obfEnabled &&
                srv.obfKey.length == 64 &&
                srv.obfKey.matches(Regex("^[0-9a-fA-F]+$"))
            ) {
                cmdArgs.add("-obf-profile"); cmdArgs.add(srv.obfProfile)
                cmdArgs.add("-obf-key"); cmdArgs.add(srv.obfKey)
            }
            if (cfg.manualCaptcha) cmdArgs.add("-manual-captcha")

            if (cfg.debugMode) cmdArgs.add("-debug")
            if (cfg.useCarrierDns) {
                val dns = activeNetworkDnsServers()
                if (dns.isNotBlank()) {
                    cmdArgs.add("-dns-servers"); cmdArgs.add(dns)
                }
            }
            // -dns-mode: plain|doh (auto — дефолт ядра, не шлём).
            if (cfg.dnsMode == DnsMode.PLAIN || cfg.dnsMode == DnsMode.DOH) {
                cmdArgs.add("-dns-mode"); cmdArgs.add(cfg.dnsMode)
            }
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
        var captchaSessionCounter = 0L

        // --- Трекинг активных соединений для индикации состояния в UI. ---
        // UDP-релей (-mode udp): каждый поток логирует свой [STREAM N] Established/Closed
        // парой (defer Closed ставится ДО логирования Established, см. client/main.go).
        // Считаем именно инкрементами, а не множеством уникальных ID: в ядре есть
        // особенность — первый поток запускается с id=1 и цикл снова итерируется
        // с i=1, из-за чего один streamID дублируется при -n N. Для счётчика это
        // безопасно (на два Established придёт два Closed), для Set это давало
        // бы заниженное число активных (N-1 вместо N).
        var udpActive = 0
        // Для UDP-релея целевое число потоков известно из конфига (-n). Если threads == 0,
        // ядро запускает один поток, считаем total = 1.
        val udpTotal = if (cfg.isRawMode) 0 else if (cfg.threads > 0) cfg.threads else 1
        var tcpActive = 0
        var tcpTotal = 0
        var isTcp = cfg.tcpForward

        fun publishStats() {
            val stats = if (isTcp) {
                ConnectionStats(tcpActive, tcpTotal)
            } else {
                ConnectionStats(udpActive, udpTotal)
            }
            ProxyServiceState.setConnectionStats(stats)
            if (currentBaseStatus == "Прокси активен") {
                buildAndShowNotification()
            }
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
                    val l = line
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
                            udpActive += 1
                            statsChanged = true
                            isTcp = false
                        }
                    }
                    STREAM_CLOSED_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            if (udpActive > 0) udpActive -= 1
                            statsChanged = true
                        }
                    }
                    TCP_TOTAL_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            tcpTotal = m.group(1)!!.toInt()
                            isTcp = true
                            statsChanged = true
                        }
                    }
                    TCP_ACTIVE_REGEX.matcher(l).let { m ->
                        if (m.find()) {
                            tcpActive = m.group(1)!!.toInt()
                            isTcp = true
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
                        val hasConnection = (isTcp && tcpActive > 0) ||
                            (!isTcp && udpActive > 0)
                        when {
                            hasFatal -> {
                                ProxyServiceState.setStartupResult(StartupResult.Failed(l))
                                updateNotification("Ошибка подключения")
                                startupFailed = true
                                startupEmitted = true
                            }
                            hasConnection -> {
                                ProxyServiceState.setStartupResult(StartupResult.Success)
                                ProxyServiceState.markConnectedIfAbsent(SystemClock.elapsedRealtime())
                                updateNotification("Прокси активен")
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

        } catch (e: CancellationException) {
            // Остановка из UI отменяет корутину serviceScope → CancellationException
            // ("Job was cancelled"). Это штатный путь остановки, не критическая ошибка.
            // Пробрасываем дальше, чтобы не ломать семантику отмены; finally ниже
            // обработает userStopped → stopSelf.
            throw e
        } catch (e: Exception) {
            // Любое исключение во время пользовательской остановки — следствие
            // destroy()/закрытия пайпов, а не реальная ошибка. Не шумим в логах.
            if (userStopped.get()) {
                startupFailed = false
            } else {
                val msg = e.message ?: ""
                if (msg.contains("error=13") || msg.contains("Permission denied")) {
                    ProxyServiceState.addLog("КРИТИЧЕСКАЯ ОШИБКА: Отказано в запуске ядра — ваше устройство блокирует выполнение файлов из внутреннего хранилища (SELinux/noexec). Используйте встроенное ядро.")
                    ProxyServiceState.setStartupResult(StartupResult.Failed(msg))
                    startupFailed = true
                } else {
                    ProxyServiceState.addLog("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
                }
            }
        } finally {
            ProxyServiceState.setCaptchaSession(null)
            cancelCaptchaNotification()
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
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!networkInitialized) {
                    networkInitialized = true
                    return
                }
                
                // Дебаунс: отменяем предыдущий ждущий перезапуск, если он был
                networkDebounceJob?.cancel()
                networkDebounceJob = serviceScope.launch {
                    kotlinx.coroutines.delay(2000)
                    if (!userStopped.get() && process.get() != null) {
                        ProxyServiceState.addLog("=== СМЕНА СЕТИ — ПЕРЕЗАПУСК ===")
                        updateNotification("Смена сети, переподключение...")
                        restartCount.set(0)
                        val p = process.get()
                        p?.destroyCompat()
                    }
                }
            }
        }
        networkCallback = cb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(cb)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
        }
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
        currentBaseStatus = text
        buildAndShowNotification()
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B/s"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB/s"
            else -> String.format(java.util.Locale.US, "%.1f MB/s", bytes / (1024f * 1024f))
        }
    }

    private fun startSpeedMonitor() {
        speedLoopJob?.cancel()
        speedLoopJob = serviceScope.launch {
            val uid = android.os.Process.myUid()
            var lastRx = android.net.TrafficStats.getUidRxBytes(uid)
            var lastTx = android.net.TrafficStats.getUidTxBytes(uid)
            while (!userStopped.get()) {
                kotlinx.coroutines.delay(1000)
                val currentRx = android.net.TrafficStats.getUidRxBytes(uid)
                val currentTx = android.net.TrafficStats.getUidTxBytes(uid)
                
                if (currentRx != android.net.TrafficStats.UNSUPPORTED.toLong() && lastRx != android.net.TrafficStats.UNSUPPORTED.toLong()) {
                    val rxSpeed = maxOf(0, currentRx - lastRx)
                    val txSpeed = maxOf(0, currentTx - lastTx)
                    currentSpeedText = "↓ ${formatSpeed(rxSpeed)} ↑ ${formatSpeed(txSpeed)}"
                    lastRx = currentRx
                    lastTx = currentTx
                    if (currentBaseStatus == "Прокси активен") {
                        buildAndShowNotification()
                    }
                }
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        var text = currentBaseStatus
        val stats = ProxyServiceState.connectionStats.value
        
        if (currentBaseStatus == "Прокси активен" || currentBaseStatus == getString(R.string.proxy_active)) {
            if (stats.total > 0) {
                text = String.format(
                    java.util.Locale.US,
                    getString(R.string.notif_proxy_status_format),
                    stats.active,
                    stats.total,
                    currentSpeedText
                )
            } else if (currentSpeedText.isNotEmpty()) {
                text += " • $currentSpeedText"
            }
        }

        val stopIntent = Intent(this, ProxyReceiver::class.java).apply {
            action = "com.freeturn.app.STOP_PROXY"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_PROXY)
            .setContentTitle(getString(R.string.notif_proxy_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notif_proxy_stop_action), stopPendingIntent)
            .build()
    }

    private fun buildAndShowNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_FG, createNotification())
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
