package com.freeturn.app.domain.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException

class SshHostKeySecurityTest {

    @Test
    fun firstKeyIsTrustedAndCaptured() {
        val repository = TofuHostKeyRepository(knownFingerprint = null)
        val session = configuredSession(repository)

        invokeJschHostCheck(session, HOST_KEY)

        assertEquals(FINGERPRINT, repository.capturedFingerprint)
    }

    @Test
    fun knownKeyIsTrustedAgain() {
        val repository = TofuHostKeyRepository(FINGERPRINT)
        val session = configuredSession(repository)

        invokeJschHostCheck(session, HOST_KEY)

        assertEquals(FINGERPRINT, repository.capturedFingerprint)
    }

    @Test
    fun changedKeyIsRejectedAtPreAuthenticationHostCheck() {
        val repository = TofuHostKeyRepository(FINGERPRINT)
        val session = configuredSession(repository)

        val error = assertThrows(InvocationTargetException::class.java) {
            invokeJschHostCheck(session, CHANGED_HOST_KEY)
        }

        assertEquals(JSchChangedHostKeyException::class.java, error.cause?.javaClass)
        assertEquals(CHANGED_FINGERPRINT, repository.capturedFingerprint)
    }

    @Test
    fun tofuConfigurationUsesStrictHostKeyChecking() {
        val session = configuredSession(TofuHostKeyRepository(null))

        assertEquals("yes", session.getConfig("StrictHostKeyChecking"))
    }

    @Test
    fun connectedFingerprintIsCheckedBeforeExecChannelIsOpened() {
        assertEquals(FINGERPRINT, verifyConnectedFingerprint(null, FINGERPRINT))
        assertEquals(FINGERPRINT, verifyConnectedFingerprint(FINGERPRINT, FINGERPRINT))
        assertThrows(IllegalStateException::class.java) {
            verifyConnectedFingerprint(FINGERPRINT, CHANGED_FINGERPRINT)
        }
        assertThrows(IllegalStateException::class.java) {
            verifyConnectedFingerprint(FINGERPRINT, null)
        }
    }

    private fun configuredSession(repository: TofuHostKeyRepository): Session =
        JSch().getSession("user", HOST, 22).also {
            configureTofuHostKeyChecking(it, repository)
        }

    private fun invokeJschHostCheck(session: Session, key: ByteArray) {
        val method = Session::class.java.getDeclaredMethod(
            "doCheckHostKey",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            ByteArray::class.java
        )
        method.isAccessible = true
        method.invoke(session, HOST, "ssh-ed25519", "unused", "ssh-ed25519", key)
    }

    private companion object {
        const val HOST = "server.example"
        val HOST_KEY = ed25519HostKey(0x11)
        val CHANGED_HOST_KEY = ed25519HostKey(0x22)
        val FINGERPRINT = fingerprint(0x11)
        val CHANGED_FINGERPRINT = fingerprint(0x22)

        fun ed25519HostKey(fill: Int): ByteArray = ByteArrayOutputStream().use { output ->
            output.writeSshString("ssh-ed25519".toByteArray())
            output.writeSshString(ByteArray(32) { fill.toByte() })
            output.toByteArray()
        }

        fun fingerprint(fill: Int): String = TofuHostKeyRepository(null).let { repository ->
            repository.check(HOST, ed25519HostKey(fill))
            checkNotNull(repository.capturedFingerprint)
        }

        fun ByteArrayOutputStream.writeSshString(value: ByteArray) {
            write(byteArrayOf(0, 0, 0, value.size.toByte()))
            write(value)
        }
    }
}
