package com.freeturn.app.domain.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogParserTest {

    // --- Капча ---

    @Test
    fun `captcha url - new core format`() {
        val events = CoreLogParser.parse("[Captcha] manually open this URL: http://localhost:8765/captcha")
        assertEquals(listOf<CoreLogEvent>(CoreLogEvent.CaptchaUrl("http://localhost:8765/captcha")), events)
    }

    @Test
    fun `captcha url - old core format`() {
        val events = CoreLogParser.parse("Open this URL in your browser: https://vk.com/captcha?id=1")
        assertEquals(listOf<CoreLogEvent>(CoreLogEvent.CaptchaUrl("https://vk.com/captcha?id=1")), events)
    }

    @Test
    fun `random localhost url is not captcha`() {
        val events = CoreLogParser.parse("listening on http://localhost:8765")
        assertTrue(events.none { it is CoreLogEvent.CaptchaUrl })
    }

    @Test
    fun `captcha resolved on auth success and failure`() {
        assertTrue(CoreLogParser.parse("[VK Auth] Success").contains(CoreLogEvent.CaptchaResolved))
        assertTrue(CoreLogParser.parse("[VK Auth] Failed").contains(CoreLogEvent.CaptchaResolved))
        assertTrue(CoreLogParser.parse("[Captcha] solve failed: timeout").contains(CoreLogEvent.CaptchaResolved))
    }

    @Test
    fun `captcha line without failed is not resolved`() {
        assertFalse(CoreLogParser.parse("[Captcha] waiting for user").contains(CoreLogEvent.CaptchaResolved))
    }

    // --- Соединения ---

    @Test
    fun `stream established and closed`() {
        assertEquals(
            listOf<CoreLogEvent>(CoreLogEvent.StreamEstablished),
            CoreLogParser.parse("[STREAM 1] Established DTLS connection")
        )
        assertEquals(
            listOf<CoreLogEvent>(CoreLogEvent.StreamClosed),
            CoreLogParser.parse("[STREAM 1] Closed DTLS connection")
        )
    }

    @Test
    fun `tcp total from waiting line`() {
        val events = CoreLogParser.parse("TCP mode: waiting for sessions to connect (total: 4)...")
        assertEquals(listOf<CoreLogEvent>(CoreLogEvent.TcpTotal(4)), events)
    }

    @Test
    fun `tcp active from session lines`() {
        assertEquals(
            listOf<CoreLogEvent>(CoreLogEvent.TcpActive(3)),
            CoreLogParser.parse("[session 2] connected (active: 3)")
        )
        assertEquals(
            listOf<CoreLogEvent>(CoreLogEvent.TcpActive(2)),
            CoreLogParser.parse("[session 2] disconnected (active: 2)")
        )
    }

    // --- Фатальный старт ---

    @Test
    fun `fatal markers detected`() {
        val lines = listOf(
            "panic: runtime error: index out of range",
            "fatal error: all goroutines are asleep",
            "ERROR: all VK credentials failed",
            "FATAL_CAPTCHA: gave up"
        )
        for (l in lines) {
            assertTrue("expected fatal: $l", CoreLogParser.parse(l).any { it is CoreLogEvent.FatalStartup })
        }
    }

    @Test
    fun `rate limit cooldown lines are not fatal`() {
        val lines = listOf(
            "identity cooldown: rate limit, waiting 30s",
            "VK throttle (rate limit), trying next credential"
        )
        for (l in lines) {
            assertTrue("not fatal: $l", CoreLogParser.parse(l).none { it is CoreLogEvent.FatalStartup })
        }
    }

    @Test
    fun `panic only at line start`() {
        assertTrue(CoreLogParser.parse("log mentions panic: in the middle").none { it is CoreLogEvent.FatalStartup })
    }

    // --- Quota ---

    @Test
    fun `quota error detected case-insensitive`() {
        assertTrue(CoreLogParser.parse("error: QUOTA exceeded").contains(CoreLogEvent.QuotaError))
        assertTrue(CoreLogParser.parse("plain line").none { it is CoreLogEvent.QuotaError })
    }

    @Test
    fun `plain line yields no events`() {
        assertTrue(CoreLogParser.parse("Connecting to TURN relay...").isEmpty())
    }
}

class CoreConnectionTrackerTest {

    @Test
    fun `udp counts established and closed`() {
        val t = CoreConnectionTracker(udpTotal = 2, tcpMode = false)
        assertFalse(t.hasConnection)
        assertEquals(0, t.active)
        assertEquals(2, t.total)

        assertTrue(t.apply(CoreLogEvent.StreamEstablished))
        assertTrue(t.apply(CoreLogEvent.StreamEstablished))
        assertEquals(2, t.active)
        assertTrue(t.hasConnection)

        assertTrue(t.apply(CoreLogEvent.StreamClosed))
        assertEquals(1, t.active)
    }

    @Test
    fun `udp active never goes negative`() {
        val t = CoreConnectionTracker(udpTotal = 1, tcpMode = false)
        t.apply(CoreLogEvent.StreamClosed)
        assertEquals(0, t.active)
    }

    @Test
    fun `duplicate stream id counts as increments`() {
        // Особенность ядра: id=1 дублируется при -n N — пара Established/Closed
        // на каждый инкремент, счётчик сходится в ноль.
        val t = CoreConnectionTracker(udpTotal = 2, tcpMode = false)
        t.apply(CoreLogEvent.StreamEstablished)
        t.apply(CoreLogEvent.StreamEstablished)
        t.apply(CoreLogEvent.StreamClosed)
        t.apply(CoreLogEvent.StreamClosed)
        assertEquals(0, t.active)
    }

    @Test
    fun `tcp mode uses parsed total and active`() {
        val t = CoreConnectionTracker(udpTotal = 1, tcpMode = true)
        assertEquals(0, t.total) // total неизвестен до waiting-строки

        assertTrue(t.apply(CoreLogEvent.TcpTotal(4)))
        assertEquals(4, t.total)
        assertFalse(t.hasConnection)

        assertTrue(t.apply(CoreLogEvent.TcpActive(2)))
        assertEquals(2, t.active)
        assertTrue(t.hasConnection)
    }

    @Test
    fun `stream event switches mode to udp`() {
        // tcpForward в конфиге, но ядро реально пошло по udp-пути.
        val t = CoreConnectionTracker(udpTotal = 3, tcpMode = true)
        t.apply(CoreLogEvent.StreamEstablished)
        assertEquals(1, t.active)
        assertEquals(3, t.total)
        assertTrue(t.hasConnection)
    }

    @Test
    fun `non-connection events do not change stats`() {
        val t = CoreConnectionTracker(udpTotal = 1, tcpMode = false)
        assertFalse(t.apply(CoreLogEvent.QuotaError))
        assertFalse(t.apply(CoreLogEvent.CaptchaResolved))
        assertFalse(t.apply(CoreLogEvent.CaptchaUrl("http://x")))
        assertFalse(t.apply(CoreLogEvent.FatalStartup("panic: x")))
    }

    @Test
    fun `raw mode total is zero`() {
        val t = CoreConnectionTracker(udpTotal = 0, tcpMode = false)
        t.apply(CoreLogEvent.StreamEstablished)
        assertEquals(1, t.active)
        assertEquals(0, t.total)
    }
}
