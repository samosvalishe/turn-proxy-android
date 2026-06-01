package com.freeturn.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.SshConfig
import com.freeturn.app.domain.ProxyOrchestrator
import com.freeturn.app.domain.SshRepository
import com.freeturn.app.ui.HapticUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ServerViewModel(
    private val sshRepository: SshRepository,
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    private val context: Context
) : ViewModel() {

    val sshState: StateFlow<SshConnectionState> = sshRepository.sshState
    val serverState: StateFlow<ServerState> = sshRepository.serverState
    val sshLog: StateFlow<List<String>> = sshRepository.sshLog
    val serverLogs: StateFlow<String?> = sshRepository.serverLogs

    val serverOpts: StateFlow<AppPreferences.ServerOpts> = prefs.serverOptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.ServerOpts())

    val sshConfig: StateFlow<SshConfig> = prefs.sshConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SshConfig())

    private val _serverInstallStage = MutableStateFlow<String?>(null)
    val serverInstallStage: StateFlow<String?> = _serverInstallStage.asStateFlow()

    private val _isRegeneratingObfKey = MutableStateFlow(false)
    val isRegeneratingObfKey: StateFlow<Boolean> = _isRegeneratingObfKey.asStateFlow()

    fun connectSsh(config: SshConfig) {
        viewModelScope.launch {
            prefs.saveSshConfig(config)
            val (success, fp) = sshRepository.connectSsh(config)
            if (success) {
                if (config.hostFingerprint.isEmpty() && fp != null) {
                    prefs.saveSshFingerprint(fp)
                }
                HapticUtil.perform(context, HapticUtil.Pattern.SUCCESS)
            } else {
                HapticUtil.perform(context, HapticUtil.Pattern.ERROR)
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
