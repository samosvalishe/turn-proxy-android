package com.freeturn.app.viewmodel.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.domain.proxy.ProxyOrchestrator
import com.freeturn.app.domain.ServerState
import com.freeturn.app.domain.SshConnectionState
import com.freeturn.app.domain.server.applyOptions
import com.freeturn.app.domain.server.withApplied
import com.freeturn.app.domain.ssh.SshRepository
import com.freeturn.app.viewmodel.HapticEvent
import com.freeturn.app.viewmodel.Haptics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(
    private val sshRepository: SshRepository,
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    private val haptics: Haptics
) : ViewModel() {

    val sshState: StateFlow<SshConnectionState> = sshRepository.sshState
    val serverState: StateFlow<ServerState> = sshRepository.serverState
    val sshLog: StateFlow<List<String>> = sshRepository.sshLog
    val logsLoading: StateFlow<Boolean> = sshRepository.logsLoading

    val serverOpts: StateFlow<ServerOpts> = prefs.serverOptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerOpts())

    val sshConfig: StateFlow<SshConfig> = prefs.sshConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SshConfig())

    // Cold-start состояния схлопнуты в Connecting, чтобы UI не мигал между фазами.
    val hubState: StateFlow<ServerHubState> =
        combine(sshState, serverState) { ssh, server ->
            when {
                ssh is SshConnectionState.Error -> ServerHubState.Failed
                server is ServerState.Error -> ServerHubState.Failed
                server is ServerState.Working -> ServerHubState.Working(server.operation)
                ssh is SshConnectionState.Connected && server is ServerState.Known ->
                    ServerHubState.Online(
                        running = server.running,
                        installed = server.installed,
                        mode = server.mode,
                        obfProfile = server.obfProfile,
                        version = server.version,
                        sshIp = ssh.ip
                    )
                else -> ServerHubState.Connecting
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerHubState.Connecting)

    fun connectSsh(config: SshConfig) {
        viewModelScope.launch {
            prefs.saveSshConfig(config)
            if (sshRepository.connectSsh(config)) {
                val active = sshRepository.activeSshConfig
                if (config.hostFingerprint.isEmpty()) {
                    active?.hostFingerprint?.takeIf { it.isNotEmpty() }
                        ?.let { prefs.saveSshFingerprint(it) }
                }
                // Детект rootMode живёт только в сессии; без персиста cleanup/share
                // (factory, читают сохранённый cfg) упирались бы в stale needs_root.
                active?.rootMode
                    ?.takeIf { it != config.rootMode }
                    ?.let { prefs.saveSshRootMode(it) }
                haptics.perform(HapticEvent.SUCCESS)
            } else {
                haptics.perform(HapticEvent.ERROR)
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

    // Установка и запуск - один apply: правки, сделанные пока сервер стоял, иначе
    // остались бы только в профиле, а сервер поднялся бы со старым install.conf.
    fun installServer() = applyActive()

    fun startServer() = applyActive()

    fun updateServer() = applyActive(update = true)

    private fun applyActive(update: Boolean = false) {
        viewModelScope.launch {
            val server = prefs.serversSnapshot.first().active ?: return@launch
            val applied = sshRepository.applyServer(server.applyOptions(), update) ?: return@launch
            orchestrator.saveApplied(server.withApplied(applied))
            orchestrator.restartProxyIfRunning()
        }
    }

    fun stopServer() {
        viewModelScope.launch { sshRepository.stopServer() }
    }

    fun fetchServerLogs(lines: Int = 200) {
        viewModelScope.launch { sshRepository.fetchServerLogs(lines) }
    }

    fun clearSshLog() = sshRepository.clearSshLog()
}
