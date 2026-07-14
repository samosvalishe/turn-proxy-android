package com.freeturn.app.domain.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

private const val CONNECT_TIMEOUT_MS = 5000

class SSHManager {

    @Volatile var lastSeenFingerprint: String? = null
        private set

    suspend fun executeSilentCommand(
        ip: String, port: Int, user: String, pass: String, command: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        execTimeoutMs: Int = 30_000
    ): String = exec(ip, port, user, pass, command, null, knownFingerprint, sshKey, execTimeoutMs)

    suspend fun executeWithStdin(
        ip: String, port: Int, user: String, pass: String,
        command: String, stdin: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        execTimeoutMs: Int = 180_000
    ): String = exec(ip, port, user, pass, command, stdin, knownFingerprint, sshKey, execTimeoutMs)

    private suspend fun exec(
        ip: String, port: Int, user: String, pass: String,
        command: String, stdin: String?,
        knownFingerprint: String?, sshKey: String, execTimeoutMs: Int
    ): String = withContext(Dispatchers.IO) {
        val tofu = TofuHostKeyRepository(knownFingerprint)
        var session: Session? = null
        try {
            session = connectSession(ip, port, user, pass, sshKey, tofu)
            lastSeenFingerprint = verifyConnectedFingerprint(knownFingerprint, tofu.capturedFingerprint)
            session.timeout = execTimeoutMs
            runCommand(session, command, stdin, execTimeoutMs)
        } catch (e: Exception) {
            mitmOrError(e, tofu, knownFingerprint)
        } finally {
            session?.disconnect()
        }
    }

    private fun connectSession(
        ip: String, port: Int, user: String, pass: String,
        sshKey: String, tofu: TofuHostKeyRepository
    ): Session {
        val jsch = JSch()
        if (sshKey.isNotBlank()) addKeyIdentity(jsch, sshKey, pass)
        val session = jsch.getSession(user, ip, port)
        if (sshKey.isBlank()) session.setPassword(pass)
        configureTofuHostKeyChecking(session, tofu)
        try {
            session.connect(CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            session.disconnect()
            throw e
        }
        return session
    }

    private fun runCommand(
        session: Session, command: String, stdin: String?, execTimeoutMs: Int
    ): String {
        val channel = session.openChannel("exec") as ChannelExec
        // stderr объединяется с stdout для единого ответа управляющего скрипта.
        channel.setCommand("exec 2>&1\n${command.toLf()}")
        if (stdin != null) {
            channel.inputStream = ByteArrayInputStream(stdin.toLf().toByteArray(Charsets.UTF_8))
        }

        val inStream = channel.inputStream
        channel.connect(execTimeoutMs)
        val output = inStream.bufferedReader().use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    append(line).append("\n")
                }
            }
        }
        channel.disconnect()
        return output.trim()
    }

    private fun mitmOrError(e: Exception, tofu: TofuHostKeyRepository, knownFingerprint: String?): String {
        val isMitm = tofu.capturedFingerprint != null &&
            knownFingerprint != null &&
            tofu.capturedFingerprint != knownFingerprint
        return if (isMitm) {
            "ERROR: Отпечаток сервера изменился - возможна MITM-атака\n" +
                "Ожидался: $knownFingerprint\n" +
                "Получен:  ${tofu.capturedFingerprint}"
        } else {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Загружает приватный ключ в JSch.
     * Нормализуем CRLF -> LF (OpenSSH требует LF + пустую строку в конце).
     * [pass] - парольная фраза ключа.
     */
    private fun addKeyIdentity(jsch: JSch, sshKey: String, pass: String) {
        val keyBytes = normalizePrivateKey(sshKey)
        val passphrase = pass.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        jsch.addIdentity("identity", keyBytes, null, passphrase)
    }

    private fun normalizePrivateKey(raw: String): ByteArray {
        val lf = raw.toLf().trim()
        val withTrailingNl = if (lf.endsWith("\n")) lf else "$lf\n"
        return withTrailingNl.toByteArray(Charsets.UTF_8)
    }
}

private fun String.toLf(): String = replace("\r\n", "\n").replace("\r", "\n")

internal fun configureTofuHostKeyChecking(
    session: Session,
    repository: TofuHostKeyRepository
) {
    session.hostKeyRepository = repository
    session.setConfig(Properties().apply {
        setProperty("StrictHostKeyChecking", "yes")
    })
}

internal fun verifyConnectedFingerprint(
    knownFingerprint: String?,
    capturedFingerprint: String?
): String {
    val received = checkNotNull(capturedFingerprint) {
        "SSH-сервер не предоставил ключ для проверки"
    }
    check(knownFingerprint == null || knownFingerprint == received) {
        "Отпечаток SSH-сервера изменился"
    }
    return received
}

internal class TofuHostKeyRepository(
    private val knownFingerprint: String?
) : HostKeyRepository {

    var capturedFingerprint: String? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        capturedFingerprint = sha256Fingerprint(key)
        return when {
            knownFingerprint == null                -> HostKeyRepository.OK
            knownFingerprint == capturedFingerprint -> HostKeyRepository.OK
            else                                    -> HostKeyRepository.CHANGED
        }
    }

    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    override fun getKnownHostsRepositoryID(): String = "TOFU"
    override fun add(hostkey: HostKey, ui: UserInfo?) {}
    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}

    private fun sha256Fingerprint(key: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hash)
    }
}
