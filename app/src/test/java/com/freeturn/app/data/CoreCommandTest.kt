package com.freeturn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCommandTest {

    // Строка в формате Mobile.configToArgs.
    private val argv = "-peer 1.2.3.4:56000 -links https://call.example/call/join/secret " +
        "-obf-profile rtpopus -obf-key 00ff -turn 5.6.7.8 -client-id abcd -sub https://sub"

    @Test
    fun masksEverySecretFlag() {
        val out = CoreCommand.redact(argv, privacy = true)
        listOf("1.2.3.4", "call.example", "00ff", "5.6.7.8", "abcd", "https://sub").forEach {
            assertFalse("leaked $it", out.contains(it))
        }
        assertTrue(out.contains("-obf-profile rtpopus"))
    }

    @Test
    fun keepsLineWithoutPrivacy() {
        assertEquals(argv, CoreCommand.redact(argv, privacy = false))
    }
}
