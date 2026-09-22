package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ControlResponse
import com.freeturn.app.data.control.ControlResponseParser
import com.freeturn.app.domain.ssh.SSHManager
import com.freeturn.app.domain.ssh.SshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Запускает [ServerCommand] на удалённом хосте, стримя install.sh через SSH stdin. */
class ServerControl(
    context: Context,
    private val ssh: SSHManager
) {
    private val appContext = context.applicationContext

    // CR ломает bash построчно - на случай, если ассет прошёл через autocrlf.
    private val script: String by lazy {
        appContext.assets.open(SCRIPT_ASSET).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .replace("\r\n", "\n")
    }

    /** Команда запуска скрипта с эскалацией по rootMode (скрипт идёт в stdin). */
    private fun remoteCmd(argv: List<String>, cfg: SshConfig): String {
        val base = "bash -s -- " + argv.joinToString(" ") { shellQuote(it) }
        return when (cfg.rootMode) {
            SshConfig.SUDO_NOPASS -> "sudo -n $base"
            // -k сбрасывает кэш timestamp - иначе sudo может не спросить пароль, и он утечёт в stderr как команда.
            SshConfig.SUDO_PASS   -> "sudo -k -S -p '' $base"
            else                  -> base
        }
    }

    // SUDO_PASS: пароль идёт ПЕРВОЙ строкой stdin (sudo -S съест её, остаток -
    // скрипт - достаётся bash). Пусто для key-auth -> sudo не пройдёт -> sudo_auth_failed.
    private fun effectiveSudoPassword(cfg: SshConfig): String =
        cfg.sudoPassword.ifBlank { if (cfg.authType == SshConfig.AUTH_PASSWORD) cfg.password else "" }

    suspend fun run(cfg: SshConfig, cmd: ServerCommand): ControlResponse = withContext(Dispatchers.IO) {
        if (cfg.ip.isBlank()) {
            return@withContext ControlResponseParser.transportFailure("no SSH config")
        }
        val stdin = if (cfg.rootMode == SshConfig.SUDO_PASS) {
            effectiveSudoPassword(cfg) + "\n" + script
        } else {
            script
        }
        val result = ssh.executeWithStdin(
            ip = cfg.ip,
            port = cfg.port,
            user = cfg.username,
            pass = cfg.password,
            command = remoteCmd(cmd.toArgv(), cfg),
            stdin = stdin,
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else "",
            // RPC молчит до конца команды, а read-timeout JSch считает тишину: get.docker.com
            // и docker pull в apply идут минутами.
            execTimeoutMs = if (cmd is ServerCommand.Apply) APPLY_TIMEOUT_MS else EXEC_TIMEOUT_MS
        )
        when (result) {
            is SshResult.Output -> ControlResponseParser.parse(result.text)
            is SshResult.Failure -> ControlResponseParser.transportFailure(result.message, result.hostKeyChanged)
        }
    }

    /**
     * Preflight: определяет [SshConfig.rootMode]. Дешёвый exec без большого скрипта.
     * null - транспортная ошибка (соединение не удалось).
     */
    suspend fun detectRootMode(cfg: SshConfig): String? = withContext(Dispatchers.IO) {
        val result = ssh.executeSilentCommand(
            cfg.ip, cfg.port, cfg.username, cfg.password,
            "id -u; command -v sudo >/dev/null 2>&1 && { sudo -n true 2>/dev/null && echo FT_SUDO_NOPASS || echo FT_SUDO_PASS; }",
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        )
        (result as? SshResult.Output)?.let { classifyRootMode(it.text) }
    }

    /** Локальные сборки сервера вшиты только в debug с `freeturnAar=local` (отладка ядра). */
    val hasLocalBinaries: Boolean by lazy {
        appContext.assets.list(LOCAL_BIN_DIR).orEmpty().isNotEmpty()
    }

    /** Загружает вшитую сборку под архитектуру хоста; успех - путь на сервере для `apply --bin`. */
    suspend fun uploadLocalBinary(cfg: SshConfig): Result<String> = withContext(Dispatchers.IO) {
        val knownFp = cfg.hostFingerprint.ifEmpty { null }
        val key = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        val uname = when (val r = ssh.executeSilentCommand(
            cfg.ip, cfg.port, cfg.username, cfg.password, "uname -m",
            knownFingerprint = knownFp, sshKey = key
        )) {
            is SshResult.Output -> r.text.lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
            is SshResult.Failure -> return@withContext uploadFailure(r.message, r.errorCode())
        }
        val asset = serverAsset(uname)
            ?: return@withContext uploadFailure(
                "архитектура хоста \"$uname\" не поддерживается",
                ServerErrorCode.UNSUPPORTED_ARCH
            )
        val bytes = runCatching { appContext.assets.open("$LOCAL_BIN_DIR/$asset").use { it.readBytes() } }
            .getOrElse {
                return@withContext uploadFailure("в приложении нет сборки $asset", ServerErrorCode.UNSUPPORTED_ARCH)
            }

        // Без sudo: /tmp доступен любому, в /opt кладёт уже скрипт под root.
        val r = ssh.executeWithBytes(
            cfg.ip, cfg.port, cfg.username, cfg.password,
            "f=\$(mktemp /tmp/freeturn-server.XXXXXX) && cat > \"\$f\" && echo \"$UPLOAD_MARK\$f\"",
            stdin = bytes,
            knownFingerprint = knownFp,
            sshKey = key,
            execTimeoutMs = APPLY_TIMEOUT_MS
        )
        when (r) {
            is SshResult.Output -> r.text.lines().map { it.trim() }
                .firstOrNull { it.startsWith(UPLOAD_MARK) }
                ?.let { Result.success(it.removePrefix(UPLOAD_MARK)) }
                ?: uploadFailure(
                    "сервер не подтвердил загрузку $asset: ${r.text.ifBlank { "пустой ответ" }}",
                    ServerErrorCode.TRANSPORT
                )
            is SshResult.Failure -> uploadFailure(r.message, r.errorCode())
        }
    }

    private fun SshResult.Failure.errorCode(): ServerErrorCode =
        if (hostKeyChanged) ServerErrorCode.HOST_KEY_CHANGED else ServerErrorCode.TRANSPORT

    private fun uploadFailure(message: String, code: ServerErrorCode): Result<Nothing> =
        Result.failure(ServerCommandException(message, code))

    /** Одинарные кавычки для bash; `'` внутри - стандартный `'\''`. */
    private fun shellQuote(s: String): String {
        if (s.isEmpty()) return "''"
        if (s.matches(Regex("^[A-Za-z0-9._:=\\-/]+$"))) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }

    companion object {
        const val SCRIPT_ASSET = "install.sh"
        private const val EXEC_TIMEOUT_MS = 180_000
        private const val APPLY_TIMEOUT_MS = 900_000
        private const val LOCAL_BIN_DIR = "server"
        private const val UPLOAD_MARK = "FT_BIN="

        /**
         * `uname -m` -> имя ассета релиза, как server_asset в install.sh. mips не берём:
         * endianness видна только на самом хосте.
         */
        fun serverAsset(uname: String): String? = when {
            uname == "x86_64" || uname == "amd64" -> "server-linux-amd64"
            uname == "aarch64" || uname == "arm64" -> "server-linux-arm64"
            uname.startsWith("armv7") -> "server-linux-armv7"
            uname.matches(Regex("i[3-6]86")) -> "server-linux-386"
            uname == "riscv64" -> "server-linux-riscv64"
            else -> null
        }

        /** Классификация вывода preflight в [SshConfig].rootMode-константу. */
        fun classifyRootMode(output: String): String {
            val euid = output.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.toIntOrNull() != null }
                ?.toIntOrNull()
            return when {
                euid == 0 -> SshConfig.ROOT
                output.contains("FT_SUDO_NOPASS") -> SshConfig.SUDO_NOPASS
                output.contains("FT_SUDO_PASS") -> SshConfig.SUDO_PASS
                // Нет sudo / неясно: bare bash, скрипт честно вернёт needs_root.
                else -> SshConfig.ROOT
            }
        }
    }
}
