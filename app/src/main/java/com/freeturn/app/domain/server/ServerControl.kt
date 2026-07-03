package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ControlResponse
import com.freeturn.app.data.control.ControlResponseParser
import com.freeturn.app.domain.ssh.SSHManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Запускает [ServerCommand] на удалённом хосте, стримя скрипт через SSH stdin. */
class ServerControl(
    context: Context,
    private val ssh: SSHManager
) {
    private val appContext = context.applicationContext

    private val script: String by lazy {
        appContext.assets.open(SCRIPT_ASSET).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
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
            return@withContext ControlResponse(proto = 2, result = "err", code = "transport", msg = "no SSH config")
        }
        val stdin = if (cfg.rootMode == SshConfig.SUDO_PASS) {
            effectiveSudoPassword(cfg) + "\n" + script
        } else {
            script
        }
        val output = ssh.executeWithStdin(
            ip = cfg.ip,
            port = cfg.port,
            user = cfg.username,
            pass = cfg.password,
            command = remoteCmd(cmd.toArgv(), cfg),
            stdin = stdin,
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        )
        ControlResponseParser.parse(output)
    }

    /**
     * Preflight: определяет [SshConfig.rootMode]. Дешёвый exec без большого скрипта.
     * null - транспортная ошибка (соединение не удалось).
     */
    suspend fun detectRootMode(cfg: SshConfig): String? = withContext(Dispatchers.IO) {
        val out = ssh.executeSilentCommand(
            cfg.ip, cfg.port, cfg.username, cfg.password,
            "id -u; command -v sudo >/dev/null 2>&1 && { sudo -n true 2>/dev/null && echo FT_SUDO_NOPASS || echo FT_SUDO_PASS; }",
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        )
        if (out.startsWith("ERROR:")) null else classifyRootMode(out)
    }

    /**
     * Заворачивает значение в одинарные кавычки для bash. Любая `'` внутри
     * заменяется на `'\''` - стандартный приём.
     */
    private fun shellQuote(s: String): String {
        if (s.isEmpty()) return "''"
        // Если строка состоит только из безопасных символов - обходимся без кавычек.
        if (s.matches(Regex("^[A-Za-z0-9._:=\\-/]+$"))) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }

    companion object {
        const val SCRIPT_ASSET = "free-turn-control.sh"

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
