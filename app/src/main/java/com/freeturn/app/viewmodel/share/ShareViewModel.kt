package com.freeturn.app.viewmodel.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.config.AccessProtocol
import com.freeturn.app.data.config.ClientId
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.share.ShareInfo
import com.freeturn.app.data.share.ShareLinkBuilder
import com.freeturn.app.data.share.SharedClient
import com.freeturn.app.domain.share.ShareRepository
import com.freeturn.app.viewmodel.HapticEvent
import com.freeturn.app.viewmodel.Haptics
import com.freeturn.app.viewmodel.uiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareResult(val userName: String, val link: String, val protocol: AccessProtocol)

data class ShareUiState(
    val servers: List<Server> = emptyList(),
    val selectedServerId: String? = null,
    val shareInfo: ShareInfo? = null,
    val infoLoading: Boolean = false,
    val infoError: String? = null,
    val userName: String = "",
    val manualClientId: String = "",
    val shareCallLink: Boolean = false,
    val callLinkToShare: String = "",
    val creating: Boolean = false,
    val createError: String? = null,
    val result: ShareResult? = null,
    val clients: List<SharedClient> = emptyList(),
    val clientsLoaded: Boolean = false,
    val clientsLoading: Boolean = false,
    val clientsError: String? = null,
    val reshareName: String? = null,
    val revokeTarget: SharedClient? = null,
    val revoking: Boolean = false
) {
    val selectedServer: Server? get() = servers.firstOrNull { it.id == selectedServerId }

    val localOnly: Boolean get() = selectedServer?.ssh?.ip?.isBlank() == true

    val ownerCallLink: String get() = selectedServer?.client?.callLink?.trim().orEmpty()

    val sharedCallLink: String get() = if (shareCallLink) callLinkToShare.trim() else ""

    val canManageUsers: Boolean get() = selectedServer != null && !localOnly

    val useWg: Boolean get() = shareInfo?.wgBackend == true

    val missingAddress: Boolean
        get() = selectedServer?.client?.serverAddress?.isBlank() == true

    val manualClientIdValid: Boolean get() = !localOnly || ClientId.isValid(manualClientId.trim())

    // Имя - ключ клиента на сервере; у ручного профиля оно только подпись в ссылке.
    val userNameValid: Boolean
        get() = if (localOnly) userName.isNotBlank() else ShareRepository.isValidName(userName)

    val canCreate: Boolean
        get() = !creating && userNameValid && selectedServer != null &&
            !missingAddress && shareInfo != null && !infoLoading && manualClientIdValid
}

class ShareViewModel(
    private val repo: ShareRepository,
    private val prefs: AppPreferences,
    private val haptics: Haptics,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    companion object {
        // = лимит valid_name в install.sh.
        const val MAX_USER_NAME_LEN = 32
        private val NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    // client-list по серверам в рамках жизни VM: повторный выбор не дёргает SSH.
    private val cache = mutableMapOf<String, ShareRepository.Snapshot>()

    init {
        viewModelScope.launch {
            prefs.serversSnapshot.collect { snap ->
                val servers = snap.list.filter {
                    it.ssh.ip.isNotBlank() || it.client.serverAddress.isNotBlank()
                }
                _uiState.update { st ->
                    val selected = st.selectedServerId
                        ?.takeIf { id -> servers.any { it.id == id } }
                        ?: snap.activeId?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull()?.id
                    val base = if (selected != st.selectedServerId) st.resetForServer(selected) else st
                    base.copy(servers = servers)
                }
                // SSH-загрузка после enter-перехода не сбивает анимацию экрана.
            }
        }
    }

    private fun ShareUiState.resetForServer(id: String?) = copy(
        selectedServerId = id,
        shareInfo = null,
        manualClientId = "",
        shareCallLink = false,
        callLinkToShare = "",
        infoError = null,
        createError = null,
        clients = emptyList(),
        clientsLoaded = false,
        clientsLoading = false,
        clientsError = null,
        reshareName = null
    )

    fun selectServer(id: String) {
        if (_uiState.value.selectedServerId == id) return
        _uiState.update { it.resetForServer(id) }
        loadIfNeeded(id)
    }

    fun setUserName(name: String) {
        val v = if (_uiState.value.localOnly) name else name.replace(NAME_CHARS, "")
        _uiState.update { it.copy(userName = v.take(MAX_USER_NAME_LEN), createError = null) }
    }

    fun setManualClientId(id: String) =
        _uiState.update { it.copy(manualClientId = id.trim().lowercase().take(32), createError = null) }

    fun setShareCallLink(enabled: Boolean) =
        _uiState.update {
            // Включили - подставляем ссылку сервера как заготовку, её можно перебить своей.
            it.copy(
                shareCallLink = enabled,
                callLinkToShare = if (enabled) it.ownerCallLink else "",
                createError = null
            )
        }

    fun setCallLinkToShare(value: String) =
        _uiState.update { it.copy(callLinkToShare = value, createError = null) }

    fun retryInfo() {
        val id = _uiState.value.selectedServerId ?: return
        cache.remove(id)
        _uiState.update { it.copy(infoError = null) }
        loadIfNeeded(id)
    }

    fun ensureInfoLoaded() {
        val st = _uiState.value
        if (st.shareInfo == null && !st.infoLoading && st.infoError == null) {
            st.selectedServerId?.let(::loadIfNeeded)
        }
    }

    // Обновляем кэш без loading-состояния, чтобы повторный вход оставался мгновенным.
    fun revalidateInfo() {
        val st = _uiState.value
        val id = st.selectedServerId ?: return
        if (st.shareInfo == null) return
        val server = st.servers.firstOrNull { it.id == id } ?: return
        if (server.ssh.ip.isBlank()) return // ручной сервер: серверной правды нет
        viewModelScope.launch {
            repo.list(server.ssh).onSuccess { fresh ->
                cache[id] = fresh
                _uiState.update { cur ->
                    if (cur.selectedServerId == id) cur.withSnapshot(fresh) else cur
                }
            }
        }
    }

    private fun ShareUiState.withSnapshot(s: ShareRepository.Snapshot) = copy(
        shareInfo = s.info,
        clients = s.clients,
        clientsLoaded = true
    )

    private fun loadIfNeeded(id: String) {
        val st = _uiState.value
        if (st.selectedServerId != id || st.infoLoading) return
        val server = st.servers.firstOrNull { it.id == id } ?: return
        if (server.ssh.ip.isBlank()) {
            // Синтетическое состояние позволяет собрать локальную ссылку без SSH.
            if (st.shareInfo == null) _uiState.update {
                it.copy(shareInfo = ShareInfo(), manualClientId = server.client.clientId)
            }
            return
        }
        cache[id]?.let { cached ->
            _uiState.update { it.withSnapshot(cached) }
            return
        }
        _uiState.update { it.copy(infoLoading = true, infoError = null) }
        viewModelScope.launch {
            val result = repo.list(server.ssh)
            result.onSuccess { cache[id] = it }
            val current = _uiState.value.selectedServerId
            if (current != id) {
                // Результат SSH относится к уже снятому выбору.
                _uiState.update { it.copy(infoLoading = false) }
                current?.let(::loadIfNeeded)
                return@launch
            }
            result
                .onSuccess { snap ->
                    _uiState.update { it.copy(infoLoading = false).withSnapshot(snap) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(infoLoading = false, infoError = e.uiError(appContext))
                    }
                }
        }
    }

    fun createShare() {
        val st = _uiState.value
        val server = st.selectedServer ?: return
        val info = st.shareInfo ?: return
        if (!st.canCreate) return
        val userName = st.userName.trim()
        if (server.ssh.ip.isBlank()) {
            commitCreated(
                serverId = server.id,
                userName = userName,
                link = ShareLinkBuilder.build(
                    server, info, userName, null, st.manualClientId.trim(), st.sharedCallLink
                ),
                wgConf = null
            )
            return
        }
        _uiState.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            repo.add(server.ssh, userName)
                .onSuccess { access ->
                    cache.remove(server.id)
                    commitCreated(
                        serverId = server.id,
                        userName = userName,
                        link = ShareLinkBuilder.build(
                            server, info, userName, access.wgConf,
                            access.client.clientId, st.sharedCallLink
                        ),
                        wgConf = access.wgConf,
                        newClient = access.client
                    )
                }
                .onFailure(::commitCreateError)
        }
    }

    private fun commitCreated(
        serverId: String,
        userName: String,
        link: String,
        wgConf: String?,
        newClient: SharedClient? = null
    ) {
        haptics.perform(HapticEvent.SUCCESS)
        _uiState.update { cur ->
            val appendable = cur.selectedServerId == serverId && cur.clientsLoaded && newClient != null
            cur.copy(
                creating = false,
                userName = "",
                result = ShareResult(userName, link, AccessProtocol.of(wgConf)),
                clients = if (appendable) cur.clients + newClient else cur.clients
            )
        }
    }

    private fun commitCreateError(e: Throwable) {
        haptics.perform(HapticEvent.ERROR)
        _uiState.update { it.copy(creating = false, createError = e.uiError(appContext)) }
    }

    fun dismissResult() = _uiState.update { it.copy(result = null) }

    fun refreshClients() {
        val st = _uiState.value
        val server = st.selectedServer ?: return
        if (st.clientsLoading || server.ssh.ip.isBlank()) return
        _uiState.update { it.copy(clientsLoading = true, clientsError = null) }
        viewModelScope.launch {
            val result = repo.list(server.ssh)
            // Результат SSH относится к уже снятому выбору.
            if (_uiState.value.selectedServerId != server.id) return@launch
            result
                .onSuccess { snap ->
                    cache[server.id] = snap
                    _uiState.update { it.copy(clientsLoading = false).withSnapshot(snap) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(clientsLoading = false, clientsError = e.uiError(appContext))
                    }
                }
        }
    }

    fun reshare(client: SharedClient) {
        val st = _uiState.value
        val server = st.selectedServer ?: return
        val info = st.shareInfo ?: return
        if (st.reshareName != null) return
        _uiState.update { it.copy(reshareName = client.name, clientsError = null) }
        viewModelScope.launch {
            val result = repo.conf(server.ssh, client.name)
            // Результат SSH относится к уже снятому выбору.
            if (_uiState.value.selectedServerId != server.id) return@launch
            result
                .onSuccess { access ->
                    _uiState.update {
                        it.copy(
                            reshareName = null,
                            result = ShareResult(
                                userName = client.name,
                                link = ShareLinkBuilder.build(
                                    server, info, client.name, access.wgConf,
                                    access.client.clientId, it.sharedCallLink
                                ),
                                protocol = AccessProtocol.of(access.wgConf)
                            )
                        )
                    }
                }
                .onFailure { e ->
                    haptics.perform(HapticEvent.ERROR)
                    _uiState.update {
                        it.copy(reshareName = null, clientsError = e.uiError(appContext))
                    }
                }
        }
    }

    fun askRevoke(client: SharedClient) = _uiState.update { it.copy(revokeTarget = client) }

    fun dismissRevoke() {
        if (_uiState.value.revoking) return
        _uiState.update { it.copy(revokeTarget = null) }
    }

    fun confirmRevoke() {
        val st = _uiState.value
        val server = st.selectedServer ?: return
        val target = st.revokeTarget ?: return
        if (st.revoking) return
        _uiState.update { it.copy(revoking = true) }
        viewModelScope.launch {
            repo.remove(server.ssh, target.name)
                .onSuccess {
                    cache.remove(server.id)
                    haptics.perform(HapticEvent.SUCCESS)
                    _uiState.update {
                        it.copy(
                            revoking = false,
                            revokeTarget = null,
                            clients = it.clients.filterNot { c -> c.name == target.name }
                        )
                    }
                }
                .onFailure { e ->
                    haptics.perform(HapticEvent.ERROR)
                    _uiState.update {
                        it.copy(
                            revoking = false,
                            revokeTarget = null,
                            clientsError = e.uiError(appContext)
                        )
                    }
                }
        }
    }
}
