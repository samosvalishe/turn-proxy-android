package com.freeturn.app

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

    /** Отпечаток, полученный при последнем вызове executeSilentCommand */
    @Volatile var lastSeenFingerprint: String? = null
        private set

    /**
     * Выполняет команду по SSH и возвращает вывод.
     *
     * @param knownFingerprint SHA-256 отпечаток хоста из сохранённых настроек.
     *   - null  → первое подключение, принимаем любой ключ и сохраняем отпечаток
     *   - строка → проверяем совпадение; при расхождении возвращаем ошибку MITM
     * @param sshKey PEM-строка приватного ключа. Если не пустая — используется key-аутентификация
     *   вместо пароля. Поддерживаются RSA, EC, Ed25519 (JSch).
     */
    suspend fun executeSilentCommand(
        ip: String, port: Int, user: String, pass: String, command: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        /**
         * Таймаут на чтение из канала (мс). Защита от зависания, когда
         * соединение установлено, но удалённый процесс молчит.
         */
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
            // StrictHostKeyChecking = "no" нужен чтобы JSch не требовал known_hosts файл;
            // реальную проверку выполняет TofuHostKeyRepository
            config["StrictHostKeyChecking"] = "no"
            tempSession.setConfig(config)
            tempSession.connect(5000)
            // Socket-таймаут на I/O: если за execTimeoutMs не пришло ни байта,
            // read() бросит SocketTimeoutException вместо вечного ожидания.
            tempSession.timeout = execTimeoutMs

            lastSeenFingerprint = tofu.capturedFingerprint

            val channel = tempSession.openChannel("exec") as ChannelExec

            // "exec 2>&1" merges stderr into stdout at shell level — no separate thread needed.
            // Also strip \r: Kotlin CRLF source files embed carriage returns into string literals
            // which corrupts variable values on the remote Linux shell.
            val sanitized = command.replace("\r\n", "\n").replace("\r", "\n")
            channel.setCommand("exec 2>&1\n$sanitized")

            // Получаем stdout-поток ДО connect() — после уже нельзя
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
            // Если отпечаток изменился — конкретное сообщение об угрозе
            val isMitm = tofu.capturedFingerprint != null
                && knownFingerprint != null
                && tofu.capturedFingerprint != knownFingerprint
            if (isMitm) {
                "ERROR: Отпечаток сервера изменился — возможна MITM-атака\n" +
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
     * Выполняет команду по SSH, передавая [stdin] через стандартный ввод процесса.
     * Используется для стрима asset-скрипта (`bash -s -- args`) без записи на сервер.
     * Возвращает stdout+stderr команды (CRLF→LF).
     */
    suspend fun executeWithStdin(
        ip: String, port: Int, user: String, pass: String,
        command: String, stdin: String,
        knownFingerprint: String? = null,
        sshKey: String = "",
        /**
         * Таймаут на чтение из канала (мс). Дефолт > чем у обычного exec,
         * потому что серверный install качает бинарь с GitHub.
         */
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
            // Нормализуем переносы строк stdin: Kotlin source может содержать
            // CRLF, что ломает bash heredoc/parser.
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
                "ERROR: Отпечаток сервера изменился — возможна MITM-атака\n" +
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
     * Загружает приватный ключ в JSch для key-аутентификации.
     *
     * OpenSSH ed25519/RSA-ключи требуют LF и финального перевода строки — при вставке
     * через Android-textfield/Windows может прилететь CRLF, и парсер OPENSSH PRIVATE KEY
     * роняет ключ. Нормализуем перед addIdentity. Парольная фраза ключа (если он
     * зашифрован) приходит в [pass]. Ed25519 требует Bouncy Castle на classpath
     * (на Android нет Java 15 EdDSA) — см. libs.versions.toml.
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
 * TOFU (Trust On First Use) репозиторий ключей хостов для JSch.
 *
 * Логика:
 * - knownFingerprint == null → первое подключение, принимаем любой ключ
 * - knownFingerprint совпадает → ОК
 * - knownFingerprint не совпадает → CHANGED, JSch бросает JSchException
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
