package com.freeturn.app.domain.proxy

sealed interface CoreLogEvent {
    data class CaptchaUrl(val url: String) : CoreLogEvent
    data object CaptchaResolved : CoreLogEvent
    data object StreamEstablished : CoreLogEvent
    data object StreamClosed : CoreLogEvent
    data class TcpTotal(val total: Int) : CoreLogEvent
    data class TcpActive(val active: Int) : CoreLogEvent
    data class FatalStartup(val line: String) : CoreLogEvent
    data object QuotaError : CoreLogEvent
}

object CoreLogParser {

    // Жесткая привязка к формату капчи (старое и новое ядро).
    private val CAPTCHA_URL_REGEX =
        Regex("""(?:manually open this URL|Open this URL in your browser):\s*(https?://\S+)""")

    private val STREAM_ESTABLISHED_REGEX =
        Regex("""\[STREAM (\d+)\] Established DTLS connection""")
    private val STREAM_CLOSED_REGEX =
        Regex("""\[STREAM (\d+)\] Closed DTLS connection""")
    private val TCP_ACTIVE_REGEX =
        Regex("""\[session \d+\] (?:connected|disconnected) \(active: (\d+)\)""")
    private val TCP_TOTAL_REGEX =
        Regex("""TCP mode: waiting for sessions to connect \(total: (\d+)\)""")

    fun parse(line: String): List<CoreLogEvent> {
        val events = mutableListOf<CoreLogEvent>()

        CAPTCHA_URL_REGEX.find(line)?.let {
            events += CoreLogEvent.CaptchaUrl(it.groupValues[1])
        }

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

        // Фатальный старт: не матчим "rate limit" (часть retry-цикла), ищем финальный отказ.
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

class CoreConnectionTracker(
    private val udpTotal: Int,
    tcpMode: Boolean
) {
    private var isTcp = tcpMode
    // Считаем инкрементами, а не Set, так как ядро может дублировать streamID (id=1).
    private var udpActive = 0
    private var tcpActive = 0
    private var tcpTotal = 0

    val active: Int get() = if (isTcp) tcpActive else udpActive
    val total: Int get() = if (isTcp) tcpTotal else udpTotal

    val hasConnection: Boolean get() = active > 0

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
