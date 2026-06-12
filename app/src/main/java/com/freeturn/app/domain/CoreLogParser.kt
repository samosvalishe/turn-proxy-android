package com.freeturn.app.domain

/** Событие, распознанное в строке лога клиентского ядра. */
sealed interface CoreLogEvent {
    /** Ядро просит ручную капчу — открыть [url]. */
    data class CaptchaUrl(val url: String) : CoreLogEvent
    /** Auth-чейн завершился (успех/провал) — текущая капча-сессия больше не нужна. */
    data object CaptchaResolved : CoreLogEvent
    /** UDP-релей: поток установил DTLS-соединение. */
    data object StreamEstablished : CoreLogEvent
    /** UDP-релей: поток закрыл DTLS-соединение. */
    data object StreamClosed : CoreLogEvent
    /** TCP-режим: целевое число сессий из waiting-строки. */
    data class TcpTotal(val total: Int) : CoreLogEvent
    /** TCP-режим: агрегированное число активных сессий. */
    data class TcpActive(val active: Int) : CoreLogEvent
    /** Фатальная ошибка старта (panic/fatal/окончательный отказ creds). */
    data class FatalStartup(val line: String) : CoreLogEvent
    /** Quota-ошибка — сигнал на сброс сессии. */
    data object QuotaError : CoreLogEvent
}

/**
 * Парсер строк лога клиентского ядра. Чистая логика без Android-зависимостей —
 * сервис реагирует на события, сам разбор тестируется юнитами.
 */
object CoreLogParser {

    // Жёстко привязываемся к строке-объявлению капчи в бинарнике, чтобы
    // случайные localhost-URL в других логах не открывали диалог.
    // Новое ядро (manual_captcha.go) пишет "manually open this URL: <url>".
    // Старое — "Open this URL in your browser: <url>". Поддерживаем оба, чтобы
    // приложение работало с кастомным ядром, оставшимся у пользователя.
    private val CAPTCHA_URL_REGEX =
        Regex("""(?:manually open this URL|Open this URL in your browser):\s*(https?://\S+)""")

    // События жизненного цикла соединений, публикуемые ядром (client/main.go).
    // UDP-релей: несколько потоков со своим [STREAM N], у каждого свой Established/Closed.
    private val STREAM_ESTABLISHED_REGEX =
        Regex("""\[STREAM (\d+)\] Established DTLS connection""")
    private val STREAM_CLOSED_REGEX =
        Regex("""\[STREAM (\d+)\] Closed DTLS connection""")
    // TCP-режим (-mode tcp): ядро само пишет агрегированное число активных сессий.
    private val TCP_ACTIVE_REGEX =
        Regex("""\[session \d+\] (?:connected|disconnected) \(active: (\d+)\)""")
    // TCP-режим: целевое число сессий приходит в этой строке до первого connected.
    // Новое ядро (tcpfwd.go): "TCP mode: waiting for sessions to connect (total: N)...".
    private val TCP_TOTAL_REGEX =
        Regex("""TCP mode: waiting for sessions to connect \(total: (\d+)\)""")

    /**
     * Все события одной строки. Маркеры независимы (как независимые if в старом
     * коде сервиса) — строка теоретически может дать несколько событий.
     */
    fun parse(line: String): List<CoreLogEvent> {
        val events = mutableListOf<CoreLogEvent>()

        CAPTCHA_URL_REGEX.find(line)?.let {
            events += CoreLogEvent.CaptchaUrl(it.groupValues[1])
        }

        // Капча-сессия закончилась: бинарник либо завершил auth-чейн (Failed/Success),
        // либо сама капча провалилась (timeout).
        if (line.contains("[VK Auth] Failed") ||
            line.contains("[VK Auth] Success") ||
            (line.contains("[Captcha]") && line.contains("failed"))
        ) {
            events += CoreLogEvent.CaptchaResolved
        }

        if (STREAM_ESTABLISHED_REGEX.containsMatchIn(line)) events += CoreLogEvent.StreamEstablished
        if (STREAM_CLOSED_REGEX.containsMatchIn(line)) events += CoreLogEvent.StreamClosed
        TCP_TOTAL_REGEX.find(line)?.let {
            events += CoreLogEvent.TcpTotal(it.groupValues[1].toInt())
        }
        TCP_ACTIVE_REGEX.find(line)?.let {
            events += CoreLogEvent.TcpActive(it.groupValues[1].toInt())
        }

        // Фатальный старт: panic/fatal/окончательный отказ получить creds.
        // ВАЖНО: не матчим подстроку "rate limit" — Go-сторона выводит её в
        // кулдаун-логах ("identity cooldown", "VK throttle ... trying next") как
        // рабочую часть retry-цикла, не как ошибку. Финальная неудача —
        // "all VK credentials failed".
        val lower = line.lowercase()
        if (lower.startsWith("panic:") ||
            lower.startsWith("fatal error:") ||
            lower.contains("all vk credentials failed") ||
            lower.contains("fatal_captcha")
        ) {
            events += CoreLogEvent.FatalStartup(line)
        }

        if (lower.contains("quota")) events += CoreLogEvent.QuotaError

        return events
    }
}

/**
 * Счётчик активных соединений ядра по [CoreLogEvent]. Режим (udp/tcp) уточняется
 * по фактическим событиям: первая stream-строка переключает в udp, первая
 * session/waiting-строка — в tcp.
 */
class CoreConnectionTracker(
    /**
     * Целевое число потоков UDP-релея — известно из конфига (-n); ядро при
     * threads == 0 запускает один поток. Для raw-режима передаётся 0 («неизвестно»).
     */
    private val udpTotal: Int,
    tcpMode: Boolean
) {
    private var isTcp = tcpMode
    // Считаем именно инкрементами, а не множеством уникальных ID: в ядре есть
    // особенность — первый поток запускается с id=1 и цикл снова итерируется
    // с i=1, из-за чего один streamID дублируется при -n N. Для счётчика это
    // безопасно (на два Established придёт два Closed), для Set это давало
    // бы заниженное число активных (N-1 вместо N).
    private var udpActive = 0
    private var tcpActive = 0
    private var tcpTotal = 0

    val active: Int get() = if (isTcp) tcpActive else udpActive
    val total: Int get() = if (isTcp) tcpTotal else udpTotal

    /** Хотя бы один живой канал — сигнал успешного старта. */
    val hasConnection: Boolean get() = active > 0

    /** Применяет событие; true — статистика изменилась и её надо опубликовать. */
    fun apply(event: CoreLogEvent): Boolean = when (event) {
        CoreLogEvent.StreamEstablished -> {
            udpActive += 1
            isTcp = false
            true
        }
        CoreLogEvent.StreamClosed -> {
            if (udpActive > 0) udpActive -= 1
            true
        }
        is CoreLogEvent.TcpTotal -> {
            tcpTotal = event.total
            isTcp = true
            true
        }
        is CoreLogEvent.TcpActive -> {
            tcpActive = event.active
            isTcp = true
            true
        }
        else -> false
    }
}
