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

class SSHManager {

    @Volatile var lastSeenFingerprint: String? = null
        private set

    suspend fun executeSilentCommand(
        ip: String, port: Int, user: String, pass: String, command: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        execTimeoutMs: Int = 30_000
    ): String = withContext(Dispatchers.IO) {
        val tofu = TofuHostKeyRepository(knownFingerprint)
        var tempSession: Session? = null
        try {
            val jsch = JSch()
            if (sshKey.isNotBlank()) addKeyIdentity(jsch, sshKey, pass)
            tempSession = jsch.getSession(user, ip, port)
            if (sshKey.isBlank()) {
                tempSession.setPassword(pass)
            }
            tempSession.hostKeyRepository = tofu

            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            tempSession.setConfig(config)
            tempSession.connect(5000)
            // SocketTimeoutException вместо вечного ожидания read().
            tempSession.timeout = execTimeoutMs

            lastSeenFingerprint = tofu.capturedFingerprint

            val channel = tempSession.openChannel("exec") as ChannelExec

            // stderr объединяется с stdout для единого ответа управляющего скрипта.
            val sanitized = command.replace("\r\n", "\n").replace("\r", "\n")
            channel.setCommand("exec 2>&1\n$sanitized")

            // JSch требует получить поток до connect().
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
            output.trim()
        } catch (e: Exception) {
            val isMitm = tofu.capturedFingerprint != null
                && knownFingerprint != null
                && tofu.capturedFingerprint != knownFingerprint
            if (isMitm) {
                "ERROR: Отпечаток сервера изменился - возможна MITM-атака\n" +
                "Ожидался: $knownFingerprint\n" +
                "Получен:  ${tofu.capturedFingerprint}"
            } else {
                "ERROR: ${e.message}"
            }
        } finally {
            tempSession?.disconnect()
        }
    }

    suspend fun executeWithStdin(
        ip: String, port: Int, user: String, pass: String,
        command: String, stdin: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        execTimeoutMs: Int = 180_000
    ): String = withContext(Dispatchers.IO) {
        val tofu = TofuHostKeyRepository(knownFingerprint)
        var tempSession: Session? = null
        try {
            val jsch = JSch()
            if (sshKey.isNotBlank()) addKeyIdentity(jsch, sshKey, pass)
            tempSession = jsch.getSession(user, ip, port)
            if (sshKey.isBlank()) tempSession.setPassword(pass)
            tempSession.hostKeyRepository = tofu
            val config = Properties()
            config["StrictHostKeyChecking"] = "no"
            tempSession.setConfig(config)
            tempSession.connect(5000)
            tempSession.timeout = execTimeoutMs

            lastSeenFingerprint = tofu.capturedFingerprint

            val channel = tempSession.openChannel("exec") as ChannelExec
            val sanitized = command.replace("\r\n", "\n").replace("\r", "\n")
            channel.setCommand("exec 2>&1\n$sanitized")
            val stdinLf = stdin.replace("\r\n", "\n").replace("\r", "\n")
            channel.inputStream = ByteArrayInputStream(stdinLf.toByteArray(Charsets.UTF_8))

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
            output.trim()
        } catch (e: Exception) {
            val isMitm = tofu.capturedFingerprint != null
                && knownFingerprint != null
                && tofu.capturedFingerprint != knownFingerprint
            if (isMitm) {
                "ERROR: Отпечаток сервера изменился - возможна MITM-атака\n" +
                "Ожидался: $knownFingerprint\n" +
                "Получен:  ${tofu.capturedFingerprint}"
            } else {
                "ERROR: ${e.message}"
            }
        } finally {
            tempSession?.disconnect()
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
        val lf = raw.replace("\r\n", "\n").replace("\r", "\n").trim()
        val withTrailingNl = if (lf.endsWith("\n")) lf else "$lf\n"
        return withTrailingNl.toByteArray(Charsets.UTF_8)
    }
}

/**
 * TOFU (Trust On First Use) репозиторий хостов.
 * null = первое подключение, несовпадение = CHANGED (JSchException).
 */
private class TofuHostKeyRepository(
    private val knownFingerprint: String?
) : HostKeyRepository {

    var capturedFingerprint: String? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        capturedFingerprint = sha256Fingerprint(key)
        return when {
            knownFingerprint == null               -> HostKeyRepository.OK
            knownFingerprint == capturedFingerprint -> HostKeyRepository.OK
            else                                   -> HostKeyRepository.CHANGED
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

