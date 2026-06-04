package com.freeturn.app.domain

import android.content.Context
import com.freeturn.app.SSHManager
import com.freeturn.app.data.SshConfig
import com.freeturn.app.domain.server.CmdResult
import com.freeturn.app.domain.server.ServerCommand
import com.freeturn.app.domain.server.ServerControl
import com.freeturn.app.domain.server.ServerOptions
import com.freeturn.app.viewmodel.ServerState
import com.freeturn.app.viewmodel.SshConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SshRepository(
    context: Context,
    private val sshManager: SSHManager = SSHManager(),
    private val serverControl: ServerControl = ServerControl(context, sshManager)
) {

    var activeSshConfig: SshConfig? = null
        private set

    private val _sshState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val sshState: StateFlow<SshConnectionState> = _sshState.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Unknown)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _serverLogs = MutableStateFlow<String?>(null)
    val serverLogs: StateFlow<String?> = _serverLogs.asStateFlow()

    private val _sshLog = MutableStateFlow<List<String>>(emptyList())
    val sshLog: StateFlow<List<String>> = _sshLog.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private fun timestamp(): String = LocalTime.now().format(timeFormatter)

    private fun appendSshLog(vararg lines: String) {
        _sshLog.update { current ->
            var next = current + lines
            if (next.size > 500) next = next.drop(next.size - 500)
            next
        }
    }

    private fun logHeader(label: String, target: String) {
        appendSshLog("", "=== $label [${timestamp()}] ===", "  ssh $target")
    }

    private fun logCmdResult(result: CmdResult) {
        result.logs.forEach { appendSshLog(it) }
        when (result) {
            is CmdResult.Ok ->
                result.kv.forEach { (k, v) -> appendSshLog("  $k=$v") }
            is CmdResult.Err ->
                appendSshLog("ERROR: ${result.message}")
        }
    }

    private suspend fun runCmd(cfg: SshConfig, label: String, cmd: ServerCommand): CmdResult {
        logHeader(label, "${cfg.username}@${cfg.ip}:${cfg.port}")
        val result = serverControl.run(cfg, cmd)
        logCmdResult(result)
        return result
    }

    /** Простая SSH-команда (используется только для healthcheck при подключении). */
    private suspend fun runEcho(cfg: SshConfig): String {
        logHeader("Подключение", "${cfg.username}@${cfg.ip}:${cfg.port}")
        val result = sshManager.executeSilentCommand(
            cfg.ip, cfg.port, cfg.username, cfg.password, "echo OK",
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == "SSH_KEY") cfg.sshKey else ""
        )
        result.lines().filter { it.isNotBlank() }.forEach { appendSshLog(it) }
        return result
    }

    suspend fun connectSsh(config: SshConfig): Pair<Boolean, String?> {
        _sshState.value = SshConnectionState.Connecting
        val result = runEcho(config)
        // Сравниваем построчно, а не весь вывод: серверный MOTD/banner или строки
        // из .bashrc могут попасть в stdout перед "OK" и сломать строгое равенство.
        return if (result.lines().any { it.trim() == "OK" }) {
            val fp = sshManager.lastSeenFingerprint ?: config.hostFingerprint
            activeSshConfig = config.copy(hostFingerprint = fp)
            _sshState.value = SshConnectionState.Connected(config.ip)
            checkServerState(config)
            Pair(true, sshManager.lastSeenFingerprint)
        } else {
            _sshState.value = SshConnectionState.Error(result.removePrefix("ERROR: "))
            Pair(false, null)
        }
    }

    fun disconnect() {
        activeSshConfig = null
        _sshState.value = SshConnectionState.Disconnected
        _serverState.value = ServerState.Unknown
    }

    suspend fun checkServerState(config: SshConfig? = null) {
        val cfg = config ?: activeSshConfig ?: return
        if (cfg.ip.isEmpty()) {
            _serverState.value = ServerState.Unknown
            return
        }
        _serverState.value = ServerState.Checking

        when (val r = runCmd(cfg, "Проверка состояния", ServerCommand.Probe)) {
            is CmdResult.Err -> _serverState.value = ServerState.Error(r.message)
            is CmdResult.Ok -> {
                val running = r.kv["RUNNING"] == "yes"
                val installed = r.kv["INSTALLED"] == "yes"
                val tcpMode = if (running) r.kv["MODE"] == "tcp" else null
                // OBF=<profile> (none|rtpopus); null если сервер не запущен.
                val obfProfile = if (running) r.kv["OBF"] else null
                val version   = r.kv["VERSION"]
                _serverState.value = ServerState.Known(
                    installed = installed,
                    running = running,
                    tcpMode = tcpMode,
                    obfProfile = obfProfile,
                    version = version
                )
            }
        }
    }

    /**
     * Идемпотентная установка: скрипт сам решает, скачивать или нет (по sha256).
     * После downloaded автоматически генерим obf-ключ — он понадобится при
     * первом start с включённой обфускацией. Возвращаемое значение — true при успехе.
     */
    suspend fun installServer(): InstallOutcome {
        val cfg = activeSshConfig ?: return InstallOutcome.Failed("not connected")
        if (cfg.ip.isEmpty()) return InstallOutcome.Failed("no SSH config")
        _serverState.value = ServerState.Working("Установка free-turn-proxy...")

        val result = runCmd(cfg, "Установка", ServerCommand.Install)
        return when (result) {
            is CmdResult.Err -> {
                _serverState.value = ServerState.Error(result.message)
                InstallOutcome.Failed(result.message)
            }
            is CmdResult.Ok -> {
                val stage = result.kv["STAGE"] ?: "ok"
                val version = result.kv["VERSION"]
                val needsRestart = result.kv["NEEDS_RESTART"] == "yes"
                // Если при update сервер был запущен, скрипт перезаписал бинарь,
                // но процесс всё ещё крутит старую версию. Перезапускаем сами,
                // иначе пользователь думает что обновился, а live-running старый.
                if (needsRestart) {
                    appendSshLog("  NEEDS_RESTART=yes → авто-рестарт сервера")
                    runCmd(cfg, "Авто-остановка перед рестартом", ServerCommand.Stop)
                    // start вызвать без аргументов нельзя — нужны listen/connect.
                    // Их знает только VM. Сигнализируем через отдельный outcome,
                    // VM решает, как стартовать (с актуальными prefs).
                }
                delay(300)
                checkServerState(cfg)
                InstallOutcome.Success(stage = stage, version = version, needsRestart = needsRestart)
            }
        }
    }

    suspend fun startServer(
        listen: String,
        connect: String,
        tcpMode: Boolean = false,
        obfProfile: String = "none",
        obfKey: String = ""
    ): Boolean {
        val cfg = activeSshConfig ?: return false
        if (cfg.ip.isEmpty()) return false

        _serverState.value = ServerState.Working("Запуск сервера...")
        val result = runCmd(
            cfg, "Запуск",
            ServerCommand.Start(
                ServerOptions(
                    listen = listen,
                    connect = connect,
                    tcpMode = tcpMode,
                    obfProfile = obfProfile,
                    obfKey = obfKey
                )
            )
        )
        if (result is CmdResult.Err) {
            _serverState.value = ServerState.Error(result.message)
            return false
        }
        delay(1500)
        checkServerState(cfg)
        return true
    }

    suspend fun stopServer() {
        val cfg = activeSshConfig ?: return
        if (cfg.ip.isEmpty()) return
        _serverState.value = ServerState.Working("Остановка сервера...")

        val result = runCmd(cfg, "Остановка", ServerCommand.Stop)
        if (result is CmdResult.Err) {
            _serverState.value = ServerState.Error(result.message)
            return
        }
        delay(1000)
        checkServerState(cfg)
    }

    fun clearServerLogs() {
        _serverLogs.value = null
    }

    suspend fun fetchServerLogs(lines: Int = 200) {
        val cfg = activeSshConfig ?: return
        if (cfg.ip.isEmpty()) return
        _serverLogs.value = "…"
        when (val r = runCmd(cfg, "server.log", ServerCommand.FetchLogs(lines))) {
            is CmdResult.Err -> _serverLogs.value = "ERROR: ${r.message}"
            is CmdResult.Ok ->
                _serverLogs.value = r.logs.joinToString("\n").ifEmpty { "(лог пуст)" }
        }
    }

    /** Просит сервер сгенерировать новый obf-key. Возвращает hex или null. */
    suspend fun generateObfKey(): String? {
        val cfg = activeSshConfig ?: return null
        if (cfg.ip.isEmpty()) return null
        return when (val r = runCmd(cfg, "Генерация obf-key", ServerCommand.GenObfKey)) {
            is CmdResult.Err -> null
            is CmdResult.Ok -> r.kv["OBFKEY"]?.takeIf {
                it.matches(Regex("^[0-9a-fA-F]{64}$"))
            }
        }
    }

    suspend fun wgInstall(): String? {
        val cfg = activeSshConfig ?: return null
        return when (val r = runCmd(cfg, "WireGuard install", ServerCommand.WgInstall)) {
            is CmdResult.Err -> null
            is CmdResult.Ok -> decodeWgConfig(r.kv["WG_CLIENT_CONFIG"])
        }
    }

    suspend fun wgShowPeer(): String? {
        val cfg = activeSshConfig ?: return null
        return when (val r = runCmd(cfg, "WireGuard show peer", ServerCommand.WgShowPeer)) {
            is CmdResult.Err -> null
            is CmdResult.Ok -> decodeWgConfig(r.kv["WG_CLIENT_CONFIG"])
        }
    }

    private fun decodeWgConfig(encoded: String?): String? {
        if (encoded == null) return null
        return try {
            String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        } catch (_: IllegalArgumentException) {
            encoded
        }
    }

    fun updateServerState(state: ServerState) {
        _serverState.value = state
    }

    fun resetAll() {
        disconnect()
        _serverLogs.value = null
        _sshLog.value = emptyList()
    }
}

sealed class InstallOutcome {
    /**
     * stage: cached | downloaded; version — только если ядро вернуло.
     * needsRestart — true, если бинарь был переустановлен поверх работающего
     * процесса; перед использованием новой версии нужен start.
     */
    data class Success(
        val stage: String,
        val version: String?,
        val needsRestart: Boolean = false
    ) : InstallOutcome()
    data class Failed(val message: String) : InstallOutcome()
}
