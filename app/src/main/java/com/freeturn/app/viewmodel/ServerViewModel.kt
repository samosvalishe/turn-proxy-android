package com.freeturn.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.SshConfig
import com.freeturn.app.domain.ProxyOrchestrator
import com.freeturn.app.domain.SshRepository
import com.freeturn.app.ui.HapticUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(
    private val sshRepository: SshRepository,
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    context: Context
) : ViewModel() {

    // applicationContext: ViewModel переживает Activity — иначе утечка.
    private val appContext = context.applicationContext

    val sshState: StateFlow<SshConnectionState> = sshRepository.sshState
    val serverState: StateFlow<ServerState> = sshRepository.serverState
    val sshLog: StateFlow<List<String>> = sshRepository.sshLog
    val serverLogs: StateFlow<String?> = sshRepository.serverLogs

    val serverOpts: StateFlow<AppPreferences.ServerOpts> = prefs.serverOptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.ServerOpts())

    val sshConfig: StateFlow<SshConfig> = prefs.sshConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SshConfig())

    private val _serverInstallStage = MutableStateFlow<String?>(null)

    private val _isRegeneratingObfKey = MutableStateFlow(false)
    val isRegeneratingObfKey: StateFlow<Boolean> = _isRegeneratingObfKey.asStateFlow()

    private val _isWgWorking = MutableStateFlow(false)
    val isWgWorking: StateFlow<Boolean> = _isWgWorking.asStateFlow()

    private val _lastWgConfig = MutableStateFlow<String?>(null)
    val lastWgConfig: StateFlow<String?> = _lastWgConfig.asStateFlow()

    /**
     * Сводный статус АКТИВНОГО сервера для хаба — одна модель из 3 потоков. Profile-контекст
     * (активность профиля, наличие SSH) добавляет экран. Промежуточные фазы коллапсятся в
     * [ServerHubStatus.Connecting]: от cold start до готовности — один переход в [ServerHubStatus.Online].
     */
    val hubStatus: StateFlow<ServerHubStatus> =
        combine(sshState, serverState, _serverInstallStage) { ssh, server, stage ->
            when {
                ssh is SshConnectionState.Error -> ServerHubStatus.Failed(ssh.message)
                server is ServerState.Error -> ServerHubStatus.Failed(server.message)
                server is ServerState.Working -> ServerHubStatus.Working(server.action)
                ssh is SshConnectionState.Connected && server is ServerState.Known ->
                    ServerHubStatus.Online(
                        running = server.running,
                        installed = server.installed,
                        tcpMode = server.tcpMode,
                        obfProfile = server.obfProfile,
                        version = server.version,
                        installStage = stage,
                        sshIp = ssh.ip
                    )
                // Disconnected / Connecting / Connected+Checking → единый busy-визуал.
                else -> ServerHubStatus.Connecting
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerHubStatus.Connecting)

    fun connectSsh(config: SshConfig) {
        viewModelScope.launch {
            prefs.saveSshConfig(config)
            val (success, fp) = sshRepository.connectSsh(config)
            if (success) {
                if (config.hostFingerprint.isEmpty() && fp != null) {
                    prefs.saveSshFingerprint(fp)
                }
                HapticUtil.perform(appContext, HapticUtil.Pattern.SUCCESS)
            } else {
                HapticUtil.perform(appContext, HapticUtil.Pattern.ERROR)
            }
        }
    }

    fun reconnectSsh() {
        viewModelScope.launch {
            val cfg = sshRepository.activeSshConfig
                 ?: sshConfig.value.takeIf { it.ip.isNotEmpty() }
                 ?: prefs.sshConfigFlow.first()
            if (cfg.ip.isNotEmpty()) connectSsh(cfg)
        }
    }

    fun installServer() {
        viewModelScope.launch {
            _serverInstallStage.value = null
            val outcome = sshRepository.installServer()
            if (outcome is com.freeturn.app.domain.InstallOutcome.Success) {
                _serverInstallStage.value = outcome.stage
                if (outcome.stage == "downloaded") {
                    val current = prefs.serverOptsFlow.first()
                    if (current.obfKey.isBlank()) {
                        val key = sshRepository.generateObfKey()
                        if (!key.isNullOrBlank()) {
                            prefs.saveServerOpts(current.copy(obfKey = key))
                        }
                    }
                }
                if (outcome.needsRestart) {
                    startServer()
                    orchestrator.restartProxyIfRunning()
                }
            }
        }
    }

    fun wgInstall() {
        viewModelScope.launch {
            if (_isWgWorking.value) return@launch
            _isWgWorking.value = true
            _lastWgConfig.value = null
            try {
                val config = sshRepository.wgInstall()
                if (config != null) {
                    val current = prefs.clientConfigFlow.first()
                    prefs.saveClientConfig(current.copy(wireGuardConfig = config))
                    _lastWgConfig.value = config
                }
            } finally {
                _isWgWorking.value = false
            }
        }
    }

    fun wgShowPeer() {
        viewModelScope.launch {
            if (_isWgWorking.value) return@launch
            _isWgWorking.value = true
            _lastWgConfig.value = null
            try {
                val config = sshRepository.wgShowPeer()
                if (config != null) {
                    val current = prefs.clientConfigFlow.first()
                    prefs.saveClientConfig(current.copy(wireGuardConfig = config))
                    _lastWgConfig.value = config
                }
            } finally {
                _isWgWorking.value = false
            }
        }
    }

    fun consumeInstallStage() { _serverInstallStage.value = null }
    fun startServer() {
        viewModelScope.launch {
            val l = prefs.proxyListenFlow.first()
            val c = prefs.proxyConnectFlow.first()
            if (!l.matches(Regex("""^[\w.\-]+:\d{1,5}$""")) || !c.matches(Regex("""^[\w.\-]+:\d{1,5}$"""))) {
                sshRepository.updateServerState(ServerState.Error("Неверный формат адреса (ожидается host:port)"))
                return@launch
            }
            val tcpMode = prefs.clientConfigFlow.first().tcpForward
            val opts = prefs.serverOptsFlow.first()
            sshRepository.startServer(
                listen = l, connect = c,
                tcpMode = tcpMode,
                obfProfile = if (opts.obfEnabled) opts.obfProfile else "none",
                obfKey = if (opts.obfEnabled) opts.obfKey else ""
            )
        }
    }

    fun stopServer() {
        viewModelScope.launch { sshRepository.stopServer() }
    }

    fun fetchServerLogs(lines: Int = 200) {
        viewModelScope.launch { sshRepository.fetchServerLogs(lines) }
    }

    fun clearServerLogs() {
        sshRepository.clearServerLogs()
    }

    fun regenerateObfKey() {
        viewModelScope.launch {
            if (_isRegeneratingObfKey.value) return@launch
            _isRegeneratingObfKey.value = true
            try {
                val key = sshRepository.generateObfKey() ?: return@launch
                val current = prefs.serverOptsFlow.first()
                val next = current.copy(obfKey = key)
                prefs.saveServerOpts(next)
                
                val clientConfig = prefs.clientConfigFlow.first()
                if (clientConfig.syncServerSwitches) orchestrator.restartServerIfRunning()
                orchestrator.restartProxyIfRunning()
            } finally {
                _isRegeneratingObfKey.value = false
            }
        }
    }
}
