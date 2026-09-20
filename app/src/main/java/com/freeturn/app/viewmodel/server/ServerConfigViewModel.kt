package com.freeturn.app.viewmodel.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.control.UninstallData
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerOpts
import com.freeturn.app.data.server.ServersSnapshot
import com.freeturn.app.domain.proxy.ProxyOrchestrator
import com.freeturn.app.domain.server.ServerSetupRepository
import com.freeturn.app.viewmodel.uiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ServerCleanupState {
    data object Idle : ServerCleanupState
    data object Running : ServerCleanupState
    data class Done(val data: UninstallData) : ServerCleanupState
    data class Error(val message: String) : ServerCleanupState
}

/**
 * Список серверов и их сохранённый конфиг: CRUD, правки клиента и серверных опций,
 * снос с VPS. Живая SSH-сессия активного сервера - в [ServerViewModel].
 */
class ServerConfigViewModel(
    private val prefs: AppPreferences,
    private val orchestrator: ProxyOrchestrator,
    private val serverSetup: ServerSetupRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    val serversSnapshot: StateFlow<ServersSnapshot> = prefs.serversSnapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServersSnapshot())

    val clientConfig: StateFlow<ClientConfig> = prefs.clientConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    val proxyListen: StateFlow<String> = prefs.proxyListenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0.0.0.0:56000")

    val proxyConnect: StateFlow<String> = prefs.proxyConnectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "127.0.0.1:40537")

    private val _cleanupState = MutableStateFlow<ServerCleanupState>(ServerCleanupState.Idle)
    val cleanupState: StateFlow<ServerCleanupState> = _cleanupState.asStateFlow()

    // Ручной сервер создаётся неактивным и с sync OFF, чтобы его можно было донастроить без SSH.
    fun addManualServer(name: String, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val server = Server(name = name, client = ClientConfig(syncServerSwitches = false))
            onAdded(prefs.addServer(server))
        }
    }

    fun renameServer(id: String, name: String) {
        viewModelScope.launch { prefs.renameServer(id, name) }
    }

    fun cloneServer(id: String, onCloned: (String) -> Unit) {
        viewModelScope.launch { prefs.cloneServer(id)?.let(onCloned) }
    }

    fun deleteServer(id: String) {
        viewModelScope.launch { prefs.deleteServer(id) }
    }

    fun applyServer(id: String) {
        viewModelScope.launch {
            val target = prefs.serversSnapshot.first().list.firstOrNull { it.id == id }
                ?: return@launch
            prefs.setActiveServerId(target.id)
            orchestrator.onActiveServerChanged(target)
        }
    }

    fun resetCleanupState() {
        _cleanupState.value = ServerCleanupState.Idle
    }

    fun cleanupServer(id: String) {
        viewModelScope.launch {
            val cfg = serversSnapshot.value.list.firstOrNull { it.id == id }?.ssh ?: return@launch
            _cleanupState.value = ServerCleanupState.Running
            // Перед удалением обновляем rootMode: сохранённое значение могло устареть.
            val mode = serverSetup.detectRootMode(cfg) ?: cfg.rootMode
            serverSetup.uninstall(cfg.copy(rootMode = mode), withWgPkg = true)
                .onSuccess { _cleanupState.value = ServerCleanupState.Done(it) }
                .onFailure { _cleanupState.value = ServerCleanupState.Error(it.uiError(appContext)) }
        }
    }

    fun updateServerClient(id: String, transform: (ClientConfig) -> ClientConfig) {
        viewModelScope.launch {
            prefs.updateServer(id) { it.copy(client = transform(it.client)) }
        }
    }

    private fun updateActiveClient(transform: (ClientConfig) -> ClientConfig) {
        viewModelScope.launch {
            prefs.updateActiveServer { it.copy(client = transform(it.client)) }
        }
    }

    fun setActiveVkLink(link: String) = updateActiveClient { it.copy(vkLink = link.trim()) }

    fun setSplitTunnelMode(value: String) = updateActiveClient { it.copy(splitTunnelMode = value) }

    fun setSplitTunnelApps(value: String) =
        updateActiveClient { it.copy(splitTunnelApps = value.trim()) }

    fun setSyncServerSwitches(enabled: Boolean) {
        viewModelScope.launch {
            val changed = prefs.updateActiveServer {
                it.copy(client = it.client.copy(syncServerSwitches = enabled))
            }
            if (changed) orchestrator.scheduleSync()
        }
    }

    fun setProxyMode(id: String?, mode: String) = updateOpts(id) { it.copy(proxyMode = mode) }

    /** Режим и ARQ одной транзакцией: рестарт пары идёт один раз на "Применить". */
    fun applyForwardConfig(id: String?, mode: String, kcp: KcpProfile) =
        updateOpts(id) { it.copy(proxyMode = mode, kcp = kcp) }

    /**
     * Режим и ARQ обязаны совпадать с сервером, поэтому правка активного сервера
     * тянет за собой рестарт обеих сторон - как в apply-модели экрана сервера.
     */
    private fun updateOpts(id: String?, transform: (ServerOpts) -> ServerOpts) {
        viewModelScope.launch {
            val changed = if (id == null) {
                prefs.updateActiveServer { it.copy(opts = transform(it.opts)) }
            } else {
                prefs.updateServer(id) { it.copy(opts = transform(it.opts)) }
            }
            if (!changed) return@launch
            if (id != null && id != prefs.serversSnapshot.first().activeId) return@launch
            orchestrator.scheduleSync()
        }
    }

    // Одна транзакция и один рестарт сохраняют атомарность apply-модели.
    fun applyServerConfig(
        listen: String,
        connect: String,
        proxyMode: String,
        obfProfile: String,
        obfKey: String,
        obfTimingMs: Int
    ) {
        viewModelScope.launch {
            val sync = prefs.clientConfigFlow.first().syncServerSwitches
            val changed = prefs.updateActiveServer {
                it.withServerConfig(listen, connect, proxyMode, obfProfile, obfKey, obfTimingMs)
            }
            if (changed) orchestrator.restartPair(syncServer = sync)
        }
    }

    // Неактивный сервер обновляется без вмешательства в рантайм активного.
    fun updateServerConfig(
        id: String,
        listen: String,
        connect: String,
        proxyMode: String,
        obfProfile: String,
        obfKey: String,
        obfTimingMs: Int
    ) {
        viewModelScope.launch {
            prefs.updateServer(id) {
                it.withServerConfig(listen, connect, proxyMode, obfProfile, obfKey, obfTimingMs)
            }
        }
    }
}

/** Пустой ключ при включённой обфускации - сохранённый, а без него свежий. */
private fun Server.withServerConfig(
    listen: String,
    connect: String,
    proxyMode: String,
    obfProfile: String,
    obfKey: String,
    obfTimingMs: Int
): Server {
    val key = obfKey.trim().ifBlank {
        opts.obfKey.ifBlank {
            if (obfProfile != ObfProfile.NONE) ObfProfile.generateKey() else ""
        }
    }
    return copy(
        proxyListen = listen,
        proxyConnect = connect,
        opts = opts.copy(
            proxyMode = proxyMode,
            obfProfile = obfProfile,
            obfKey = key,
            obfTimingMs = obfTimingMs
        )
    )
}
