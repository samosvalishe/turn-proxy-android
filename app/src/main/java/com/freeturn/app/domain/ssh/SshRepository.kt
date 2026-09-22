package com.freeturn.app.domain.ssh

import android.content.Context
import com.freeturn.app.data.config.ProxyMode
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.control.ControlResponse
import com.freeturn.app.data.control.LogsData
import com.freeturn.app.data.control.ProbeData
import com.freeturn.app.domain.ServerOperation
import com.freeturn.app.domain.ServerState
import com.freeturn.app.domain.SshConnectionState
import com.freeturn.app.domain.server.ApplyOptions
import com.freeturn.app.domain.server.ApplyResult
import com.freeturn.app.domain.server.ServerCommand
import com.freeturn.app.domain.server.ServerControl
import com.freeturn.app.domain.server.applyResult
import com.freeturn.app.domain.server.errorText
import com.freeturn.app.domain.server.requireData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SshRepository(context: Context, private val sshManager: SSHManager) {

    private val serverControl = ServerControl(context, sshManager)

    // Параллельные команды на одном SSHManager затирали бы fingerprint/_serverState.
    private val mutex = Mutex()

    var activeSshConfig: SshConfig? = null
        private set

    private val _sshState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val sshState: StateFlow<SshConnectionState> = _sshState.asStateFlow()

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Unknown)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _sshLog = MutableStateFlow<List<String>>(emptyList())
    val sshLog: StateFlow<List<String>> = _sshLog.asStateFlow()

    private val _logsLoading = MutableStateFlow(false)
    val logsLoading: StateFlow<Boolean> = _logsLoading.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private fun timestamp(): String = LocalTime.now().format(timeFormatter)

    private fun appendSshLog(lines: List<String>) {
        if (lines.isEmpty()) return
        _sshLog.update { current ->
            var next = current + lines
            if (next.size > 500) next = next.drop(next.size - 500)
            next
        }
    }

    private fun appendSshLog(vararg lines: String) = appendSshLog(lines.toList())

    private fun logHeader(label: String, target: String) {
        appendSshLog("", "=== $label [${timestamp()}] ===", "  ssh $target")
    }

    private fun logCmdResult(result: ControlResponse) {
        // Одним обновлением состояния: построчный append на длинном выводе (журнал
        // на 200 строк) дёргал подписчиков UI (бейдж и автоскролл) на каждую строку.
        val batch = buildList {
            addAll(result.logs)
            if (result.isOk) {
                // Только примитивы: в объектах (config, owner) лежат obf-ключ и приватный WG-конфиг.
                result.data.forEach { (k, v) ->
                    val p = v as? JsonPrimitive ?: return@forEach
                    if (k != "obf_key") add("  $k=" + (p.contentOrNull ?: p.toString()))
                }
            } else {
                add("ERROR: ${result.errorText()}")
            }
        }
        appendSshLog(batch)
    }

    private suspend fun runCmd(cfg: SshConfig, label: String, cmd: ServerCommand): ControlResponse {
        logHeader(label, "${cfg.username}@${cfg.ip}:${cfg.port}")
        val result = serverControl.run(cfg, cmd)
        logCmdResult(result)
        return result
    }

    private suspend fun runEcho(cfg: SshConfig): SshResult {
        logHeader("Подключение", "${cfg.username}@${cfg.ip}:${cfg.port}")
        val result = sshManager.executeSilentCommand(
            cfg.ip, cfg.port, cfg.username, cfg.password, "echo OK",
            knownFingerprint = cfg.hostFingerprint.ifEmpty { null },
            sshKey = if (cfg.authType == SshConfig.AUTH_SSH_KEY) cfg.sshKey else ""
        )
        when (result) {
            is SshResult.Output -> appendSshLog(result.text.lines().filter { it.isNotBlank() })
            is SshResult.Failure -> appendSshLog("ERROR: ${result.message}")
        }
        return result
    }

    suspend fun connectSsh(config: SshConfig): Boolean = mutex.withLock {
        _sshState.value = SshConnectionState.Connecting
        val result = runEcho(config)
        // Сравниваем построчно, а не весь вывод: серверный MOTD/banner или строки
        // из .bashrc могут попасть в stdout перед "OK" и сломать строгое равенство.
        if (result is SshResult.Output && result.text.lines().any { it.trim() == "OK" }) {
            val fp = sshManager.lastSeenFingerprint ?: config.hostFingerprint
            // Сохранённый rootMode мог устареть между SSH-сессиями.
            val mode = serverControl.detectRootMode(config) ?: config.rootMode
            activeSshConfig = config.copy(hostFingerprint = fp, rootMode = mode)
            _sshState.value = SshConnectionState.Connected(config.ip)
            checkServerStateLocked(activeSshConfig, silent = false)
            true
        } else {
            _sshState.value = when (result) {
                is SshResult.Failure -> SshConnectionState.Error(result.message, result.hostKeyChanged)
                is SshResult.Output -> SshConnectionState.Error(result.text)
            }
            false
        }
    }

    fun disconnect() {
        activeSshConfig = null
        _sshState.value = SshConnectionState.Disconnected
        _serverState.value = ServerState.Unknown
    }

    /**
     * @param silent пропустить промежуточный [ServerState.Checking]. Нужно при перепроверке
     * после действия (стоп/старт/установка): иначе хаб моргает Working -> "Подключение" -> Online.
     * При silent текущий Working держится до прихода [ServerState.Known].
     */
    suspend fun checkServerState(config: SshConfig? = null, silent: Boolean = false) =
        mutex.withLock { checkServerStateLocked(config, silent) }

    private suspend fun checkServerStateLocked(config: SshConfig?, silent: Boolean) {
        val cfg = config ?: activeSshConfig ?: return
        if (cfg.ip.isEmpty()) {
            _serverState.value = ServerState.Unknown
            return
        }
        if (!silent) _serverState.value = ServerState.Checking

        val r = runCmd(cfg, "Проверка состояния", ServerCommand.Probe)
        r.requireData<ProbeData>()
            .onSuccess { d ->
                val c = d.config
                _serverState.value = ServerState.Known(
                    installed = d.installed && c != null,
                    running = d.running,
                    mode = if (d.running) c?.mode?.ifBlank { null } ?: ProxyMode.UDP else null,
                    obfProfile = if (d.running) c?.obfProfile else null,
                    version = d.version
                )
            }
            .onFailure { e -> _serverState.value = ServerState.Error(e.message ?: r.errorText()) }
    }

    suspend fun applyServer(opts: ApplyOptions, update: Boolean = false): ApplyResult? = mutex.withLock {
        val cfg = activeSshConfig ?: return@withLock null
        if (cfg.ip.isEmpty()) return@withLock null
        var bin = ""
        if (update && serverControl.hasLocalBinaries) {
            _serverState.value = ServerState.Working(ServerOperation.UPLOAD_BUILD)
            logHeader("Загрузка локальной сборки сервера", "${cfg.username}@${cfg.ip}:${cfg.port}")
            bin = serverControl.uploadLocalBinary(cfg).getOrElse { e ->
                val msg = e.message ?: e.toString()
                appendSshLog("ERROR: $msg")
                _serverState.value = ServerState.Error(msg)
                return@withLock null
            }
            appendSshLog("  bin=$bin")
        }
        _serverState.value = ServerState.Working(
            if (update) ServerOperation.UPDATE else ServerOperation.APPLY
        )
        val result = runCmd(cfg, if (update) "Обновление" else "Применение", ServerCommand.Apply(opts, update, bin))
        result.applyResult()
            .onSuccess { checkServerStateLocked(cfg, silent = true) }
            .onFailure { _serverState.value = ServerState.Error(result.errorText()) }
            .getOrNull()
    }

    suspend fun stopServer() = mutex.withLock {
        val cfg = activeSshConfig ?: return@withLock
        if (cfg.ip.isEmpty()) return@withLock
        _serverState.value = ServerState.Working(ServerOperation.STOP)
        val result = runCmd(cfg, "Остановка", ServerCommand.Stop)
        if (!result.isOk) {
            _serverState.value = ServerState.Error(result.errorText())
            return@withLock
        }
        checkServerStateLocked(cfg, silent = true)
    }

    suspend fun fetchServerLogs(lines: Int = 200) = mutex.withLock {
        val cfg = activeSshConfig ?: return@withLock
        if (cfg.ip.isEmpty()) return@withLock
        _logsLoading.value = true
        try {
            logHeader("Журнал сервера", "${cfg.username}@${cfg.ip}:${cfg.port}")
            val result = serverControl.run(cfg, ServerCommand.Logs(lines))
            result.requireData<LogsData>()
                .onSuccess { appendSshLog(it.lines) }
                .onFailure { appendSshLog("ERROR: ${result.errorText()}") }
        } finally {
            _logsLoading.value = false
        }
        Unit
    }

    fun updateServerState(state: ServerState) {
        _serverState.value = state
    }

    fun logNote(line: String) {
        appendSshLog("", "--- $line [${timestamp()}]")
    }

    fun clearSshLog() {
        _sshLog.value = emptyList()
    }

    fun resetAll() {
        disconnect()
        _sshLog.value = emptyList()
    }
}
