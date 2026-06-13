package com.freeturn.app.domain.server

import android.content.Context
import com.freeturn.app.domain.ssh.SSHManager
import com.freeturn.app.data.config.SshConfig
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

    /** Сырая команда для запуска (без скрипта). */
    private fun remoteCmd(argv: List<String>): String {
        val quoted = argv.joinToString(" ") { shellQuote(it) }
        return "bash -s -- $quoted"
    }

    suspend fun run(cfg: SshConfig, cmd: ServerCommand): CmdResult = withContext(Dispatchers.IO) {
        if (cfg.ip.isBlank()) return@withContext CmdResult.Err("no SSH config", emptyList())
        // Сборка команды и чтение скрипта на IO-потоке.
        val output = ssh.executeWithStdin(
            ip = cfg.ip,
            port = cfg.port,
            user = cfg.username,
            pass = cfg.password,
            command = remoteCmd(cmd.toArgv()),
            stdin = script,
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        )
        ServerOutputParser.parse(output)
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
    }
}
