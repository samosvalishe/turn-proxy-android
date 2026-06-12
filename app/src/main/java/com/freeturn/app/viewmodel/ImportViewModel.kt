package com.freeturn.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Server
import com.freeturn.app.data.ServerOpts
import com.freeturn.app.data.SshConfig
import com.freeturn.app.data.TunnelTransport
import com.freeturn.app.data.share.FreeturnLink
import com.freeturn.app.domain.LinkImportBus
import com.freeturn.app.ui.HapticUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    /** Распарсенная ссылка; null — sheet скрыт. */
    val link: FreeturnLink? = null,
    val serverName: String = "",
    /** Звонок получателя — обязателен (в ссылку не входит, уникален на клиента). */
    val vkLink: String = "",
    /** Сервер с таким же адресом уже есть — предупреждение, не блокировка. */
    val duplicateAddress: Boolean = false,
    /** Точное совпадение WG-conf — этот доступ уже импортирован. */
    val duplicateConf: Boolean = false,
    /** Ссылка не распарсилась (показывается отдельным диалогом). */
    val parseError: Boolean = false,
    val saving: Boolean = false,
    /** Сохранение упало (например, битый DataStore) — sheet остаётся открытым. */
    val saveError: Boolean = false,
    /** Импорт завершён — success-состояние sheet. */
    val saved: Boolean = false
) {
    val canConfirm: Boolean
        get() = link != null && !saving && !saved && vkLink.isNotBlank()
}

/**
 * Импорт сервера по freeturn://-ссылке. Слушает [LinkImportBus] (deep link,
 * QR, вставка) — sheet всплывает из любого места приложения. Импортированный
 * сервер не управляется (пустой SSH) — только клиентское подключение.
 */
class ImportViewModel(
    private val prefs: AppPreferences,
    bus: LinkImportBus,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bus.links.collect(::offer)
        }
    }

    private suspend fun offer(raw: String) {
        if (_uiState.value.saving) return
        FreeturnLink.parse(raw).fold(
            onSuccess = { link ->
                val servers = prefs.serversSnapshot.first().list
                val normalizedConf = link.wgConf.trim()
                _uiState.value = ImportUiState(
                    link = link,
                    serverName = link.name.ifBlank { link.peer.substringBefore(':') },
                    duplicateAddress = servers.any {
                        it.client.serverAddress.equals(link.peer, ignoreCase = true)
                    },
                    duplicateConf = normalizedConf.isNotEmpty() && servers.any {
                        it.client.wireGuardConfig.trim() == normalizedConf
                    }
                )
            },
            onFailure = {
                HapticUtil.perform(appContext, HapticUtil.Pattern.ERROR)
                _uiState.value = ImportUiState(parseError = true)
            }
        )
    }

    fun setServerName(name: String) = _uiState.update { it.copy(serverName = name) }

    fun setVkLink(value: String) = _uiState.update { it.copy(vkLink = value) }

    fun confirm(fallbackName: String) {
        val st = _uiState.value
        val link = st.link ?: return
        if (!st.canConfirm) return
        _uiState.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                prefs.addServer(buildServer(link, st, fallbackName), activate = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HapticUtil.perform(appContext, HapticUtil.Pattern.ERROR)
                _uiState.update { it.copy(saving = false, saveError = true) }
                return@launch
            }
            HapticUtil.perform(appContext, HapticUtil.Pattern.SUCCESS)
            _uiState.update { it.copy(saving = false, saved = true) }
        }
    }

    fun dismiss() {
        if (_uiState.value.saving) return
        _uiState.value = ImportUiState()
    }

    private fun buildServer(link: FreeturnLink, st: ImportUiState, fallbackName: String): Server {
        val wgConf = link.wgConf.trim()
        return Server(
            name = st.serverName.trim().ifBlank { fallbackName },
            // Пустой SSH = неуправляемый сервер: хаб скрывает серверные операции.
            ssh = SshConfig(),
            client = ClientConfig(
                serverAddress = link.peer,
                vkLink = st.vkLink.trim(),
                provider = link.provider,
                tcpForward = link.mode == "tcp",
                bond = link.bond,
                useUdp = link.transport == "udp",
                streamsPerCred = link.streamsPerCred.takeIf { it > 0 }
                    ?: ClientConfig.DEFAULT_STREAMS_PER_CRED,
                tunnelTransport = if (wgConf.isNotEmpty()) TunnelTransport.WIREGUARD
                else TunnelTransport.NONE,
                wireGuardConfig = wgConf,
                // cid из ссылки — владелец уже посадил его в allowlist сервера.
                clientId = link.clientId.trim()
            ),
            opts = ServerOpts(
                obfProfile = link.obfProfile.ifBlank { ObfProfile.NONE },
                obfKey = link.obfKey
            )
        )
    }
}
