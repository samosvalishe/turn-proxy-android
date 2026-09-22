package com.freeturn.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.freeturn.app.R
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.CoreCommand
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.Provider
import com.freeturn.app.data.config.coreDnsServers
import com.freeturn.app.data.config.toCoreJson
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.domain.proxy.LogLevel
import com.freeturn.app.domain.proxy.ProxyEngine
import com.freeturn.app.domain.proxy.ProxyLog
import com.freeturn.app.domain.proxy.ProxyStore
import com.freeturn.app.domain.proxy.SocketProtector
import com.freeturn.app.domain.proxy.Socks5Server
import com.freeturn.app.domain.proxy.TunHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import org.koin.android.ext.android.inject

/**
 * Foreground-`VpnService`: держит tun-интерфейс и жизненный цикл сессии
 * [ProxyEngine]. Трафик идёт мимо - его ведёт ядро.
 *
 * Сессия адресуется id ядра ([session]): отмена корутины её не рвёт (`establish`
 * и вызовы ядра блокирующие), поэтому каждый шаг сверяется с текущим id, а
 * отменённую заявку ядро отбрасывает само.
 */
class ProxyService : VpnService() {

    private val prefs: AppPreferences by inject()
    private val engine: ProxyEngine by inject()
    private val store: ProxyStore by inject()
    private val log: ProxyLog by inject()

    private lateinit var scope: CoroutineScope
    private lateinit var notifier: ProxyNotifier
    private lateinit var network: NetworkHandoverMonitor

    private var tun: ParcelFileDescriptor? = null
    private var socks5: Socks5Server? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Сколько устройство успело проспать к прошлой проверке: разница elapsedRealtime
    // (идёт во сне) и uptimeMillis (стоит) - и есть накопленный сон.
    @Volatile private var sleptMillis = 0L

    // Нотификация должна сказать про туннель раньше, чем метрики его увидят.
    @Volatile private var tunnelMode = false
    // null - сессия ещё не прочла конфиг.
    @Volatile private var provider: String? = null
    // Остановка решена: всё, что поднимет хвост уже начатого старта, сворачиваем сразу.
    @Volatile private var stopping = false
    // Заявка ядру на текущую сессию: гасим по ней именно свою, а не следующую.
    @Volatile private var session = 0L
    // Гасимся всегда по последнему startId: свежий START делает остановку неактуальной.
    @Volatile private var lastStartId = 0
    // Сессию сворачивает либо STOP, либо onDestroy - кто успел первым.
    private val shutdownDone = AtomicBoolean(false)

    // Экран зажёгся после глубокого сна - аллокации протухли, пинаем ядро сразу, не
    // дожидаясь его гэп-детектора (тик 30 c). Короткие блокировки экрана пропускаем:
    // рецикл на каждой разблокировке рвал бы живые стримы на ровном месте.
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val slept = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
            val gap = slept - sleptMillis
            sleptMillis = slept
            if (gap < DEEP_SLEEP_KICK_MS) return
            // Длительность сна - опора при разборе отвалов: по ней видно, пережила ли
            // аллокация паузу и не мы ли сами её выбросили.
            log.add("Пробуждение после сна ${gap / 1000} c - будим ядро")
            if (!stopping) engine.wake(session)
        }
    }

    /** Ядро закрывает то, что ему отдали, поэтому наружу уходит только копия. */
    private val tunHandle = TunHandle { checkNotNull(tun).dup().detachFd() }
    private val protector = SocketProtector { fd -> protect(fd) }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        notifier = ProxyNotifier(this)
        network = NetworkHandoverMonitor(applicationContext, scope, log) { onNetworkHandover() }
        sleptMillis = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
        // Только динамически: SCREEN_ON манифестом не ловится.
        ContextCompat.registerReceiver(
            this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch { observeStatus() }
        scope.launch { observeCoreErrors() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = when {
            intent == null -> "null-intent"
            intent.action == ProxyActions.START -> "START"
            intent.action == ProxyActions.STOP -> "STOP"
            else -> "system/other"
        }
        log.add("Вход сервиса: pid=${android.os.Process.myPid()} source=$source flags=$flags startId=$startId")
        // Отмена могла догнать ещё не обработанный START: гасимся, не поднимая ядро.
        // stopSelf(startId), а не stopSelf(): START, пришедший следом за отменой,
        // делает её неактуальной - иначе он поднял бы сессию в умирающем сервисе.
        if (intent?.action == ProxyActions.STOP) {
            prefs.setProxyDesired(false)
            shutdown("команда STOP")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        lastStartId = startId

        // startForeground - первым, иначе ForegroundServiceDidNotStartInTimeException.
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, ProxyNotifier.NOTIF_ID_FG, notifier.build(), type)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException и родня: сессии не будет.
            fail(getString(R.string.proxy_error_service, e.detail()))
            return START_NOT_STICKY
        }

        // START - всегда свежая сессия (настройки могли поменяться); пустой intent -
        // возврат после sticky-рестарта, там поднимаем, только если ядро не живёт.
        if (intent?.action == ProxyActions.START || !engine.isRunning) {
            val previous = session
            val next = engine.newSession()
            session = next
            val fresh = intent?.action == ProxyActions.START
            // Инстанс мог уже свернуть сессию (STOP при забинденном сервисе его не
            // уничтожает): для новой сессии он снова рабочий, флаги снимаем.
            stopping = false
            shutdownDone.set(false)
            scope.launch {
                // Sticky-рестарт после отказа: fail() снял намерение, а система вернула
                // сервис. Без этой проверки он поднимал сессию заново - и так по кругу,
                // сжигая персону и кредиты провайдера на каждом витке.
                val desired = if (fresh) null else prefs.proxyDesiredFlow.first()
                if (!isCurrent(next)) return@launch
                log.add("Сессия $next: fresh=$fresh desired=$desired")
                if (!fresh && desired == false) {
                    if (!isCurrent(next)) return@launch
                    log.add("Сервис возвращён системой, но прокси выключен - не поднимаем")
                    shutdown("возврат сервиса без намерения")
                    stopSelf(lastStartId)
                    return@launch
                }
                // Прошлая сессия могла ещё подниматься: сначала ядро отпускает свою
                // копию fd, только потом закрываем прошлый интерфейс.
                engine.stop(previous)
                if (!isCurrent(next)) return@launch
                // Хвост прошлой сессии целиком: раньше снимался только tun, а её SOCKS5
                // оставался на порту - новая падала бы с "Address already in use".
                releaseSessionOf(next)
                startSession(next, fresh)
            }
        }
        return START_STICKY
    }

    /** [fresh] - команда пользователя; иначе это возврат сервиса после смерти процесса. */
    private suspend fun startSession(session: Long, fresh: Boolean) {
        // Один снимок профиля: раздельные чтения смешали бы peer одного сервера с obf другого.
        val server = prefs.activeServerFlow.first()
        val cfg = server?.client ?: ClientConfig()
        val opts = server?.opts ?: ServerOpts()
        if (!isCurrent(session)) return
        // Лог рестарта не чистим: строка "Процесс запущен" от App - единственный след того,
        // что процесс убивали, и после clearScreen от неё ничего бы не осталось.
        if (fresh) log.clearScreen()
        val startReason = if (fresh) "команда START" else "возврат сервиса"
        log.add("Сессия $session: запуск ($startReason), pid=${android.os.Process.myPid()}")

        if (cfg.serverAddress.isBlank() || (cfg.provider == Provider.RELAY && cfg.callLink.isBlank())) {
            fail(getString(R.string.proxy_error_not_configured))
            return
        }

        val json = buildConfigJson(cfg, opts)
        if (!isCurrent(session)) return
        val argv = try {
            engine.configToArgs(json)
        } catch (e: Exception) {
            if (!isCurrent(session)) return
            fail(getString(R.string.proxy_error_config_rejected, e.detail()))
            return
        }
        log.add("Команда: ${CoreCommand.redact(argv, prefs.privacyModeFlow.first())}")

        if (!isCurrent(session)) return

        acquireWakeLock()
        logEnvironment()
        // Метка фиксирует незавершённую сессию, причину выхода сообщает ОС.
        prefs.setCleanExit(false)
        scope.launch { heartbeat(session) }
        network.register()

        tunnelMode = cfg.wireGuardActive
        provider = cfg.provider
        // Раздача только поверх туннеля: без tun сокеты сервера ушли бы напрямую.
        val hotspot = tunnelMode && prefs.hotspotProxyEnabledFlow.first()
        if (tunnelMode) {
            // На старте интерфейс обязателен: без него сессии просто нет.
            when (val tunResult = openTun(cfg, session, hotspot)) {
                is TunResult.Failed -> {
                    if (!isCurrent(session)) return
                    fail(tunResult.message)
                    return
                }
                TunResult.Stale -> return
                TunResult.Ok -> Unit
            }
        }

        val started = try {
            engine.start(session, json, tun?.let { tunHandle }, protector)
        } catch (e: Exception) {
            if (!isCurrent(session)) return
            fail(getString(R.string.proxy_error_core_start, e.detail()))
            return
        }
        // Заявку отменили, пока поднимался интерфейс: ядро её не взяло, интерфейс не нужен.
        if (!started) {
            closeTunOf(session)
            return
        }
        log.add("Сессия $session: запуск принят ядром")
        if (hotspot) startHotspot(session)
    }

    /**
     * Раздача поднимается последней и только для актуальной сессии: ядро уже взяло
     * заявку, а пока оно поднималось, её могли отменить.
     */
    @Synchronized
    private fun startHotspot(session: Long) {
        if (!isCurrent(session)) return
        // Порт занимает ровно один сервер: потерянный тут экземпляр держал бы 1080 до
        // смерти процесса.
        socks5?.stop()
        socks5 = Socks5Server(protect = { socket -> protect(socket) }, log = log).also { it.start() }
    }

    /** Исход попытки поднять tun. Судьбу сессии решает вызывающий, а не сама попытка. */
    private sealed interface TunResult {
        data object Ok : TunResult
        /** Заявку отменили, пока поднимался интерфейс - жаловаться не на что. */
        data object Stale : TunResult
        data class Failed(val message: String) : TunResult
    }

    private fun openTun(cfg: ClientConfig, session: Long, hotspot: Boolean): TunResult {
        val setup = try {
            engine.parseTunnel(cfg.wireGuardConfig, ClientConfig.WG_MTU)
        } catch (e: Exception) {
            return TunResult.Failed(getString(R.string.proxy_error_tunnel_config, e.detail()))
        }

        val pfd = try {
            Builder().applyTunnel(applicationContext, cfg, setup, hotspot).establish()
        } catch (e: Exception) {
            return TunResult.Failed(getString(R.string.proxy_error_tunnel_iface, e.detail()))
        }
        // null - пользователь не дал согласия: старт из тайла, виджета или
        // broadcast'а идёт мимо экрана, где его спрашивают.
        if (pfd == null) return TunResult.Failed(getString(R.string.notif_proxy_vpn_permission))

        return if (adoptTun(pfd, session)) TunResult.Ok else TunResult.Stale
    }

    /**
     * Дескриптор принимает только актуальная сессия: `establish` блокирующий, и
     * её могли отменить, пока он поднимал интерфейс - тогда закрываем сразу, иначе
     * VPN остался бы висеть до смерти процесса.
     */
    @Synchronized
    private fun adoptTun(pfd: ParcelFileDescriptor, session: Long): Boolean {
        if (!isCurrent(session)) {
            pfd.close()
            return false
        }
        tun?.close()
        tun = pfd
        return true
    }

    private suspend fun buildConfigJson(cfg: ClientConfig, opts: ServerOpts): String = cfg.toCoreJson(
        srv = opts,
        carrierDns = if (cfg.useCarrierDns) network.physicalDnsServers() else null,
        ownClientId = prefs.ownClientId(),
    )

    private fun onNetworkHandover() {
        // stopping, а не только isRunning: остановка идёт в фоне, и ядро всё ещё живо -
        // без проверки рестарт поднимал бы сессию, которую сворачивают.
        if (stopping || !engine.isRunning) return
        val slept = (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis() - sleptMillis) / 1000
        log.add("Смена сети - переподключение (сон с прошлой проверки $slept c)")
        withCoreDns { session, dns -> engine.reconnect(session, dns) }
    }

    private fun withCoreDns(apply: (session: Long, dns: String) -> Unit) {
        val session = this.session
        scope.launch {
            val cfg = prefs.clientConfigFlow.first()
            if (!isCurrent(session)) return@launch
            apply(session, cfg.coreDnsServers { network.physicalDnsServers() }.joinToString(","))
        }
    }

    /** Нотификация ведётся тем же состоянием, что видит UI. */
    private suspend fun observeStatus() {
        store.status.collect { status ->
            // После решения об остановке молчим: нотификация уже снята.
            if (stopping) return@collect
            notifier.update(status, tunnelMode, provider)
        }
    }

    private suspend fun observeCoreErrors() {
        store.coreErrors.collect { error ->
            if (!isCurrent(error.session)) return@collect
            shutdown("ошибка ядра: ${error.message}")
            stopSelf(lastStartId)
        }
    }

    /** Заявка ещё актуальна? Отменённая молчит: её ошибки уже не про текущую сессию. */
    private fun isCurrent(session: Long) = !stopping && session == this.session

    /** Свой интерфейс, а не чужой: следующая сессия могла уже поднять и принять свой. */
    @Synchronized
    private fun closeTunOf(session: Long) {
        if (isCurrent(session)) closeTun()
    }

    @Synchronized
    private fun closeTun() {
        tun?.close()
        tun = null
    }

    /**
     * Без исключения из оптимизации батареи система в Doze игнорирует wake lock и режет
     * приложению сеть, поэтому статус нужен в логе рядом с моментом отвала.
     */
    private fun logEnvironment() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        log.add(
            "Окружение: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
                "батарея-исключение=${pm.isIgnoringBatteryOptimizations(packageName)}, " +
                "doze=${pm.isDeviceIdleMode}"
        )
    }

    /**
     * Метка живого процесса. Обрыв этих строк - точный момент, когда процесс заморозили
     * или убили: остальной лог в этот момент уже молчит, и отличить одно от другого
     * иначе нечем.
     */
    private suspend fun heartbeat(session: Long) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        while (isCurrent(session)) {
            delay(HEARTBEAT_MS)
            if (!isCurrent(session)) return
            val slept = (SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()) / 1000
            log.add(
                "hb up=${SystemClock.elapsedRealtime() / 1000}s сон=${slept}s doze=${pm.isDeviceIdleMode}",
                LogLevel.Plain
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // Без таймаута: сессия живёт дольше суток, release гарантирован в shutdown.
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FreeTurn::Session").apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    /** Всегда false - удобно возвращать из веток, где сессия не состоялась. */
    private fun fail(message: String): Boolean {
        // Уже гасимся - об отменённой сессии сообщать нечего.
        if (stopping) return false
        stopping = true
        // Сессия не состоялась по своей вине (конфиг, отказ системы) - восстанавливать
        // нечего: без вмешательства пользователя следующая попытка упрётся в то же самое.
        prefs.setProxyDesired(false)
        log.add(message, LogLevel.Error)
        store.fail(message)
        shutdown(message)
        stopSelf(lastStartId)
        return false
    }

    /**
     * Сворачивает сессию. Зовётся из обработки STOP, а не только из [onDestroy]:
     * пока tun поднят, система держит `VpnService` забинденным, и `stopSelf` его
     * не уничтожает - надеясь на `onDestroy`, мы оставляли бы ядро крутиться с
     * открытой копией дескриптора, а тот держал бы VPN, а VPN - сервис.
     *
     * Идемпотентна: STOP и следующий за ним onDestroy не должны гасить дважды.
     */
    @Synchronized
    private fun shutdown(reason: String) {
        if (!shutdownDone.compareAndSet(false, true)) return
        stopping = true
        val session = this.session
        notifier.cancelCaptcha()
        // Причина обязательна: по логу после гибернации надо отличать команду пользователя
        // от ошибки ядра и от отзыва VPN системой.
        log.add("Сессия $session: остановка ($reason)")
        prefs.setCleanExit(true)
        store.finish()
        releaseAll()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Процессный scope движка: onDestroy и onStartCommand блокировать нельзя, а
        // свой scope сервис вот-вот отменит. Гасим именно свою сессию - ядро уже могло
        // перейти к следующей. Этим же снимается заявка, не дошедшая до ядра.
        engine.stopAsync(session)
    }

    /**
     * Ресурсы сессии [session] - слушающий порт раздачи и tun. Чужие не трогает:
     * следующая сессия могла уже поднять свои, и она же их и освободит.
     */
    @Synchronized
    private fun releaseSessionOf(session: Long) {
        if (isCurrent(session)) releaseAll()
    }

    /**
     * Безусловно - сервис уходит и обязан отпустить всё.
     *
     * Свою копию fd отпускаем сразу, не дожидаясь ядра: `Mobile.stop` ждёт сессию (в
     * туннеле - секунды), а зависни он совсем - интерфейс остался бы поднятым до
     * смерти процесса. Ядро продолжает писать в свою копию, она валидна.
     */
    @Synchronized
    private fun releaseAll() {
        network.unregister()
        socks5?.stop()
        socks5 = null
        closeTun()
    }

    /**
     * VPN перехватило другое приложение (или пользователь отключил его в системных
     * настройках). Дефолт зовёт `stopSelf` мимо [shutdown] - ядро осталось бы крутиться
     * с открытой копией tun-дескриптора. Намерение снимаем: восстанавливать сессию,
     * которую только что отобрали, значит драться с системой.
     */
    override fun onRevoke() {
        prefs.setProxyDesired(false)
        log.add("VPN отключён системой", LogLevel.Warning)
        shutdown("VPN отозван системой")
        stopSelf(lastStartId)
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdown("сервис уничтожен")
        // Регистрация могла не состояться, если onCreate упал раньше.
        runCatching { unregisterReceiver(screenOn) }
        scope.cancel()
    }

    private companion object {
        // Тот же порог, что у гэп-детектора ядра: сон короче аллокации переживают.
        const val DEEP_SLEEP_KICK_MS = 60_000L
        const val HEARTBEAT_MS = 60_000L
    }
}

/** Без message - хотя бы тип исключения. */
internal fun Throwable.detail(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
