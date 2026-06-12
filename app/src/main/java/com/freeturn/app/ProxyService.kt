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
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.CoreArgs
import com.freeturn.app.domain.CoreConnectionTracker
import com.freeturn.app.domain.CoreLogEvent
import com.freeturn.app.domain.CoreLogParser
import com.freeturn.app.domain.WireGuardTunnelManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

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
        // Даём TURN-туннелю «устаканиться» перед поднятием WireGuard поверх него.
        private const val WIREGUARD_START_DELAY_MS = 2_000L
        // Игнорируем сетевые события первые секунды после регистрации колбэка —
        // иначе initial onAvailable/onCapabilitiesChanged триггерят ложный рестарт.
        private const val NETWORK_CALLBACK_WARMUP_MS = 3_000L
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
    @Volatile private var networkDebounceJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastPhysicalNetworkKey: String? = null
    private val restartCount = AtomicInteger(0)
    @Volatile private var captchaNotificationActive = false

    private val prefs: AppPreferences by inject()
    private lateinit var serviceScope: CoroutineScope
    private lateinit var wireGuard: WireGuardTunnelManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        wireGuard = WireGuardTunnelManager(applicationContext)
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
        // ВАЖНО: startForeground вызываем ПЕРВЫМ делом и БЕЗУСЛОВНО — любой return
        // до него даёт ForegroundServiceDidNotStartInTimeException через ~5с.
        if (openAppIntent == null) {
            openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        }
        currentBaseStatus = getString(R.string.notif_proxy_connecting)
        // Явно передаём FGS-тип (specialUse доступен с API 34; ниже — без типа).
        // Оборачиваем в try/catch: ForegroundServiceStartNotAllowedException (API 31+
        // при старте из фона) и InvalidForegroundServiceTypeException не должны
        // ронять процесс.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID_FG, createNotification(), fgsType)
        } catch (e: Exception) {
            ProxyServiceState.addLog("Не удалось запустить foreground-сервис: ${e.message}")
            ProxyServiceState.setStartupResult(StartupResult.Failed(e.message ?: "FGS start failed"))
            ProxyServiceState.setRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

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
        // Освобождаем предыдущий wakelock перед созданием нового — иначе при
        // повторном onStartCommand с уже мёртвым process старый объект остаётся
        // held до GC (утечка wakelock).
        if (wakeLock?.isHeld == true) wakeLock?.release()
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
        ProxyServiceState.setLogsEnabled(cfg.logsEnabled)
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
            // argv строит общий с UI билдер ([CoreArgs.client]) — показанная команда не
            // расходится с реально запускаемой. DNS оператора резолвим здесь (зависит от
            // активной сети) и передаём в билдер.
            val carrierDns = if (cfg.useCarrierDns) activeNetworkDnsServers() else null
            cmdArgs.addAll(CoreArgs.client(cfg, srv, carrierDns, prefs.ownClientId()))
        }

        var exitCode = -1
        val startedAt = System.currentTimeMillis()
        var startupEmitted = false
        var startupFailed = false
        var wireGuardStarted = false
        var captchaSessionCounter = 0L

        // --- Трекинг активных соединений для индикации состояния в UI. ---
        // UDP-релей (-mode udp): каждый поток логирует свой [STREAM N] Established/Closed
        // парой (defer Closed ставится ДО логирования Established, см. client/main.go).
        // Для UDP-релея целевое число потоков известно из конфига (-n). Если threads == 0,
        // ядро запускает один поток, считаем total = 1.
        val tracker = CoreConnectionTracker(
            udpTotal = if (cfg.isRawMode) 0 else if (cfg.threads > 0) cfg.threads else 1,
            tcpMode = cfg.tcpForward
        )

        fun publishStats() {
            ProxyServiceState.setConnectionStats(ConnectionStats(tracker.active, tracker.total))
            if (currentBaseStatus == getString(R.string.proxy_active)) {
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

                    // Разбор строки — в CoreLogParser (чистая логика, под юнит-тестами);
                    // здесь только реакция на события.
                    val events = CoreLogParser.parse(l)
                    var statsChanged = false
                    for (event in events) when (event) {
                        // Детекция URL ручной капчи. Каждый раз выдаём новый sessionId,
                        // чтобы диалог пересоздавал WebView, даже если URL не поменялся
                        // (бинарник всегда использует http://localhost:8765).
                        is CoreLogEvent.CaptchaUrl -> {
                            captchaSessionCounter += 1
                            ProxyServiceState.setCaptchaSession(
                                CaptchaSession(event.url, captchaSessionCounter)
                            )
                            // Показываем нотификацию только если предыдущая капча уже закрыта.
                            // Бинарник может выдать несколько URL подряд за одну авторизацию —
                            // не плодим спам.
                            if (!captchaNotificationActive) {
                                showCaptchaNotification()
                                captchaNotificationActive = true
                            }
                        }
                        // Закрываем диалог — следующая капча-сессия откроет его заново
                        // через новый sessionId.
                        CoreLogEvent.CaptchaResolved -> {
                            if (ProxyServiceState.captchaSession.value != null) {
                                ProxyServiceState.setCaptchaSession(null)
                                cancelCaptchaNotification()
                            }
                        }
                        else -> if (tracker.apply(event)) statsChanged = true
                    }
                    if (statsChanged) publishStats()

                    // Startup: ядро упало с panic/fatal/окончательно не смогло
                    // получить creds ДО того, как удалось подключиться — считаем
                    // запуск неудачным. Первая строка без этих маркеров больше не
                    // трактуется как Success (ядро могло написать "Connecting..."
                    // и только потом упасть).
                    if (!startupEmitted) {
                        val hasFatal = events.any { it is CoreLogEvent.FatalStartup }
                        val hasConnection = tracker.hasConnection
                        when {
                            hasFatal -> {
                                ProxyServiceState.setStartupResult(StartupResult.Failed(l))
                                updateNotification("Ошибка подключения")
                                startupFailed = true
                                startupEmitted = true
                            }
                            hasConnection -> {
                                try {
                                    if (cfg.wireGuardActive) {
                                        ProxyServiceState.addLog(
                                            "WireGuard: ждём ${WIREGUARD_START_DELAY_MS}мс после поднятия TURN-туннеля"
                                        )
                                        kotlinx.coroutines.delay(WIREGUARD_START_DELAY_MS)
                                        if (userStopped.get() || process.get() !== proc) {
                                            ProxyServiceState.addLog(
                                                "WireGuard: старт отменён, прокси уже останавливается"
                                            )
                                            break
                                        }
                                    }
                                    wireGuard.startAfterProxyReady(cfg)
                                    wireGuardStarted = cfg.wireGuardActive
                                    ProxyServiceState.setStartupResult(StartupResult.Success)
                                    ProxyServiceState.markConnectedIfAbsent(SystemClock.elapsedRealtime())
                                    updateNotification(
                                        if (wireGuardStarted) getString(R.string.proxy_active_wireguard)
                                        else getString(R.string.proxy_active)
                                    )
                                } catch (e: Exception) {
                                    val message = e.message ?: e.javaClass.simpleName
                                    ProxyServiceState.addLog("WireGuard: ошибка запуска: $message")
                                    ProxyServiceState.setStartupResult(
                                        StartupResult.Failed("WireGuard не запустился: $message")
                                    )
                                    updateNotification("Ошибка WireGuard")
                                    startupFailed = true
                                    proc.destroyCompat()
                                }
                                startupEmitted = true
                            }
                        }
                    }

                    // compareAndSet гарантирует единственный postDelayed даже при параллельных quota-ошибках
                    if (events.any { it is CoreLogEvent.QuotaError } &&
                        sessionKillScheduled.compareAndSet(false, true)) {
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
            // WG-туннель живёт только поверх работающего прокси — гасим вместе с ядром.
            if (wireGuardStarted) wireGuard.stop()
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
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val registeredAt = SystemClock.elapsedRealtime()
        lastPhysicalNetworkKey = physicalNetworkKey(cm)

        // Перезапуск только когда реально меняется ФИЗИЧЕСКАЯ сеть (Wi-Fi↔LTE и т.п.).
        // WireGuard поднимает свой VPN-интерфейс — без фильтрации NOT_VPN его появление
        // выглядело бы как смена сети и уводило прокси в бесконечный рестарт.
        fun schedulePhysicalNetworkCheck(reason: String) {
            if (SystemClock.elapsedRealtime() - registeredAt < NETWORK_CALLBACK_WARMUP_MS) {
                return
            }
            networkDebounceJob?.cancel()
            networkDebounceJob = serviceScope.launch {
                kotlinx.coroutines.delay(2_000)
                val oldKey = lastPhysicalNetworkKey
                val newKey = physicalNetworkKey(cm)
                // Ключ тот же — ожидаемый no-op. onCapabilitiesChanged сыплет
                // десятки раз/мин (сигнал, link speed, валидация инета), не логаем.
                if (oldKey == newKey) return@launch
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

    /**
     * Ключ ОДНОЙ приоритетной физсети (транспорт + iface). Берём приоритетную, а не
     * весь allNetworks: при активном Wi-Fi cellular флапает в фоне, набор прыгал бы →
     * ложная «смена сети» → лишний рестарт. link-адреса не в ключе — ротация
     * IPv6/DHCP идёт на той же сети. Реальный хендовер меняет транспорт/iface.
     */
    private fun physicalNetworkKey(cm: ConnectivityManager): String? {
        // allNetworks deprecated с API 31, но это единственный синхронный способ
        // снять полный снимок текущих сетей внутри колбэка. Подавляем осознанно.
        @Suppress("DEPRECATION")
        return cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                return@mapNotNull null
            }
            // Приоритет = индекс, меньше важнее.
            val (priority, transport) = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0 to "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1 to "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2 to "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3 to "bluetooth"
                else -> return@mapNotNull null
            }
            val iface = cm.getLinkProperties(network)?.interfaceName.orEmpty()
            // tie-break по iface — детерминированный выбор при равном приоритете.
            Triple(priority, iface, "$transport|$iface")
        }.minWithOrNull(compareBy({ it.first }, { it.second }))?.third
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
                kotlinx.coroutines.delay(3000)
                val currentRx = android.net.TrafficStats.getUidRxBytes(uid)
                val currentTx = android.net.TrafficStats.getUidTxBytes(uid)
                
                if (currentRx != android.net.TrafficStats.UNSUPPORTED.toLong() && lastRx != android.net.TrafficStats.UNSUPPORTED.toLong()) {
                    val rxSpeed = maxOf(0, currentRx - lastRx)
                    val txSpeed = maxOf(0, currentTx - lastTx)
                    currentSpeedText = "↓ ${formatSpeed(rxSpeed)} ↑ ${formatSpeed(txSpeed)}"
                    lastRx = currentRx
                    lastTx = currentTx
                    if (currentBaseStatus == getString(R.string.proxy_active)) {
                        buildAndShowNotification()
                    }
                }
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        var text = currentBaseStatus
        val stats = ProxyServiceState.connectionStats.value
        
        if (currentBaseStatus == getString(R.string.proxy_active)) {
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
        // WG-teardown — блокирующий JNI/IO; держать им main-поток нельзя (ANR).
        // serviceScope гасим сразу, поэтому стоп туннеля уводим на отдельный поток
        // без ожидания: туннель опустится сам, а если процесс прибьют — VpnService
        // снимет ОС вместе с процессом.
        val wg = wireGuard
        Thread { runBlocking { wg.stop() } }.start()
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
