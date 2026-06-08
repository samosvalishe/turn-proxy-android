package com.freeturn.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.ProxyService
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Profile
import com.freeturn.app.data.ProfileJson
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.domain.AppUpdater
import com.freeturn.app.domain.LocalProxyManager
import com.freeturn.app.domain.ProxyOrchestrator
import com.freeturn.app.domain.SshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val proxyManager: LocalProxyManager,
    private val sshRepository: SshRepository,
    private val appUpdater: AppUpdater,
    private val orchestrator: ProxyOrchestrator,
    context: Context
) : ViewModel() {

    // ViewModel переживает пересоздание Activity — храним applicationContext,
    // чтобы не утекала Activity.
    private val appContext = context.applicationContext

    val clientConfig: StateFlow<ClientConfig> = prefs.clientConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    val proxyListen: StateFlow<String> = prefs.proxyListenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0.0.0.0:56000")

    val proxyConnect: StateFlow<String> = prefs.proxyConnectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "127.0.0.1:40537")

    val dynamicTheme: StateFlow<Boolean> = prefs.dynamicThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val profilesSnapshot: StateFlow<ProfilesSnapshot> = prefs.profilesSnapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesSnapshot())

    val updateState: StateFlow<UpdateState> = appUpdater.state

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initialOnboardingDone = MutableStateFlow(false)
    val initialOnboardingDone: StateFlow<Boolean> = _initialOnboardingDone.asStateFlow()

    private val _initialTgSubscribeShown = MutableStateFlow(false)
    val initialTgSubscribeShown: StateFlow<Boolean> = _initialTgSubscribeShown.asStateFlow()

    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val profileMutex = Mutex()

    init {
        viewModelScope.launch {
            val done = prefs.onboardingDoneFlow.first()
            val tgShown = prefs.tgSubscribeShownFlow.first()
            _initialOnboardingDone.value = done
            _initialTgSubscribeShown.value = tgShown
            // Восстанавливаем сохранённое состояние тоггла логов при старте.
            ProxyServiceState.setLogsEnabled(prefs.clientConfigFlow.first().logsEnabled)
            _isInitialized.value = true
        }
        viewModelScope.launch {
            appUpdater.checkForUpdate(silent = true)
        }
    }

    fun setPrivacyMode(enabled: Boolean) { _privacyMode.value = enabled }

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicTheme(enabled) }
    }

    fun setTgSubscribeShown() {
        viewModelScope.launch { prefs.setTgSubscribeShown() }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }

    // expectedActiveId — профиль, который правил экран. Сменился за время дебаунса —
    // сейв отбрасываем, иначе старые поля затрут чужой профиль. null — онбординг.
    fun saveClientConfig(config: ClientConfig, expectedActiveId: String? = null) {
        viewModelScope.launch {
            val saved = profileMutex.withLock {
                val activeId = prefs.profilesSnapshot.first().activeId
                if (expectedActiveId != null && expectedActiveId != activeId) return@withLock false
                persistClient(config)
                true
            }
            if (saved) ProxyServiceState.setLogsEnabled(config.logsEnabled)
        }
    }

    // Во всех сеттерах read-modify-write выполняется ВНУТРИ profileMutex: чтение
    // current вне лока давало lost update — два параллельных сеттера читали один
    // снапшот, затем по очереди писали свой copy(), и второй затирал первого.
    fun setProvider(value: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.clientConfigFlow.first()
                if (current.provider == value) return@withLock
                persistClient(current.copy(provider = value))
            }
        }
    }

    fun setSplitTunnelMode(value: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.clientConfigFlow.first()
                if (current.splitTunnelMode == value) return@withLock
                persistClient(current.copy(splitTunnelMode = value))
            }
        }
    }

    fun setSplitTunnelApps(value: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.clientConfigFlow.first()
                val trimmed = value.trim()
                if (current.splitTunnelApps == trimmed) return@withLock
                persistClient(current.copy(splitTunnelApps = trimmed))
            }
        }
    }

    fun saveProxyServerConfig(listen: String, connect: String) {
        viewModelScope.launch {
            profileMutex.withLock { prefs.saveProxyConfig(listen, connect); mirrorActiveProfile() }
        }
    }

    // --- Profiles ---
    fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val ssh = prefs.sshConfigFlow.first()
                val client = prefs.clientConfigFlow.first()
                val listen = prefs.proxyListenFlow.first()
                val connect = prefs.proxyConnectFlow.first()
                val server = prefs.serverOptsFlow.first()
                val base = name.trim().ifBlank {
                    serverAddrToProfileName(client.serverAddress)
                }
                val profile = Profile(
                    id = UUID.randomUUID().toString(),
                    name = uniqueProfileName(base, current.list, null),
                    ssh = ssh,
                    client = client,
                    proxyListen = listen,
                    proxyConnect = connect,
                    server = server
                )
                prefs.saveProfiles(current.list + profile)
                prefs.setActiveProfileId(profile.id)
            }
        }
    }

    private suspend fun mirrorActiveProfile() {
        val current = prefs.profilesSnapshot.first()
        val activeId = current.activeId ?: return
        val ssh = prefs.sshConfigFlow.first()
        val client = prefs.clientConfigFlow.first()
        val listen = prefs.proxyListenFlow.first()
        val connect = prefs.proxyConnectFlow.first()
        val server = prefs.serverOptsFlow.first()
        val updated = current.list.map {
            if (it.id == activeId)
                it.copy(
                    ssh = ssh,
                    client = client,
                    proxyListen = listen,
                    proxyConnect = connect,
                    server = server
                )
            else it
        }
        if (updated != current.list) prefs.saveProfiles(updated)
    }

    // Пишет legacy + зеркалит в активный профиль. Только под profileMutex; НЕ из
    // applyProfile/deleteProfile (там грузится снимок, mirror словил бы гонку).
    private suspend fun persistClient(config: ClientConfig) {
        prefs.saveClientConfig(config)
        mirrorActiveProfile()
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val target = current.list.firstOrNull { it.id == id } ?: return@withLock
                val base = name.trim().ifBlank { target.name }
                val unique = uniqueProfileName(base, current.list, id)
                val updated = current.list.map {
                    if (it.id == id) it.copy(name = unique) else it
                }
                prefs.saveProfiles(updated)
            }
        }
    }

    fun applyProfile(id: String) {
        viewModelScope.launch {
            val target = profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val t = current.list.firstOrNull { it.id == id } ?: return@withLock null
                prefs.saveSshConfig(t.ssh)
                prefs.saveClientConfig(t.client)
                prefs.saveProxyConfig(t.proxyListen, t.proxyConnect)
                prefs.saveServerOpts(t.server)
                prefs.setActiveProfileId(t.id)
                t
            } ?: return@launch

            val serverRunning = (sshRepository.serverState.value as? ServerState.Known)?.running == true
            if (serverRunning) orchestrator.restartServerIfRunning()

            if (ProxyServiceState.isRunning.value) {
                proxyManager.stopProxy()
                withTimeoutOrNull(2_000) {
                    ProxyServiceState.isRunning.first { !it }
                }
                proxyManager.startProxy(target.client)
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val remaining = current.list.filterNot { it.id == id }
                prefs.saveProfiles(remaining)
                if (current.activeId == id) {
                    val next = remaining.firstOrNull()
                    prefs.setActiveProfileId(next?.id)
                    if (next != null) {
                        prefs.saveSshConfig(next.ssh)
                        prefs.saveClientConfig(next.client)
                        prefs.saveProxyConfig(next.proxyListen, next.proxyConnect)
                        prefs.saveServerOpts(next.server)
                    }
                }
            }
        }
    }

    fun exportProfile(id: String): String? {
        val profile = profilesSnapshot.value.list.firstOrNull { it.id == id } ?: return null
        return ProfileJson.exportToJson(listOf(profile))
    }

    fun exportAllProfiles(): String {
        return ProfileJson.exportToJson(profilesSnapshot.value.list)
    }

    fun importProfiles(json: String): Int {
        val imported = ProfileJson.importFromJson(json)
        if (imported.isEmpty()) return 0
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val merged = current.list.toMutableList()
                for (p in imported) {
                    val idx = merged.indexOfFirst { it.id == p.id }
                    if (idx >= 0) merged[idx] = p else merged.add(p)
                }
                prefs.saveProfiles(merged)
            }
        }
        return imported.size
    }

    private fun serverAddrToProfileName(serverAddr: String): String =
        serverAddr.substringBefore(':').takeIf { it.isNotBlank() }
            ?: appContext.getString(com.freeturn.app.R.string.profile_default_name)

    private fun uniqueProfileName(base: String, existing: List<Profile>, excludingId: String?): String {
        val taken = existing
            .filter { it.id != excludingId }
            .map { it.name.trim().lowercase() }
            .toSet()
        val trimmed = base.trim()
        if (trimmed.lowercase() !in taken) return trimmed
        var i = 2
        while ("$trimmed ($i)".lowercase() in taken) i++
        return "$trimmed ($i)"
    }

    // --- Edit-by-id (новый Settings-флоу) ---
    // Правит client-конфиг профиля по id, НЕ требуя его активации. Для активного
    // профиля дополнительно зеркалит в legacy-ключи (рантаймовое ядро их читает) и
    // обновляет глобальный флаг логов. Для неактивного — пишет только сохранённый
    // снимок, рантайм не трогается (нужно для редактирования неактивных серверов).
    fun updateProfileClient(id: String, transform: (ClientConfig) -> ClientConfig) {
        viewModelScope.launch {
            var mirrored: ClientConfig? = null
            profileMutex.withLock {
                val snap = prefs.profilesSnapshot.first()
                val target = snap.list.firstOrNull { it.id == id } ?: return@withLock
                val updated = transform(target.client)
                if (updated == target.client) return@withLock
                val list = snap.list.map { if (it.id == id) it.copy(client = updated) else it }
                prefs.saveProfiles(list)
                if (id == snap.activeId) {
                    prefs.saveClientConfig(updated)
                    mirrored = updated
                }
            }
            mirrored?.let { ProxyServiceState.setLogsEnabled(it.logsEnabled) }
        }
    }

    // --- Server & Proxy Switches (TcpForward, Bond, Obf, Sync) ---
    fun setBond(enabled: Boolean) {
        viewModelScope.launch {
            val changed = profileMutex.withLock {
                val current = prefs.clientConfigFlow.first()
                if (current.bond == enabled) return@withLock false
                persistClient(current.copy(bond = enabled))
                true
            }
            if (changed) orchestrator.restartProxyIfRunning()
        }
    }

    fun setSyncServerSwitches(enabled: Boolean) {
        viewModelScope.launch {
            val changed = profileMutex.withLock {
                val current = prefs.clientConfigFlow.first()
                if (current.syncServerSwitches == enabled) return@withLock false
                persistClient(current.copy(syncServerSwitches = enabled))
                true
            }
            if (changed && enabled) {
                orchestrator.restartServerIfRunning()
                orchestrator.restartProxyIfRunning()
            }
        }
    }

    /**
     * Атомарно применяет конфиг сервера (proxy-адреса + tcp-проброс + профиль обфускации
     * + obf-ключ) одной транзакцией и перезапускает сервер/прокси РОВНО один раз — для
     * apply-модели «Настроек сервера» (натыкали черновик → Применить). Если включается
     * обфускация без ключа в sync-режиме, ключ генерируется на сервере (SSH, вне mutex).
     */
    fun applyServerConfig(
        listen: String,
        connect: String,
        tcpForward: Boolean,
        obfProfile: String,
        obfKey: String
    ) {
        viewModelScope.launch {
            val sync = prefs.clientConfigFlow.first().syncServerSwitches
            val trimmedKey = obfKey.trim()
            val generatedKey: String? =
                if (obfProfile != ObfProfile.NONE && trimmedKey.isBlank() && sync)
                    sshRepository.generateObfKey()?.takeIf { it.isNotBlank() }
                else null
            var changed = false
            profileMutex.withLock {
                if (prefs.proxyListenFlow.first() != listen || prefs.proxyConnectFlow.first() != connect) {
                    prefs.saveProxyConfig(listen, connect); changed = true
                }
                val client = prefs.clientConfigFlow.first()
                if (client.tcpForward != tcpForward) {
                    prefs.saveClientConfig(client.copy(tcpForward = tcpForward)); changed = true
                }
                val opts = prefs.serverOptsFlow.first()
                val effKey = trimmedKey.ifBlank { generatedKey ?: opts.obfKey }
                val nextOpts = opts.copy(obfProfile = obfProfile, obfKey = effKey)
                if (nextOpts != opts) { prefs.saveServerOpts(nextOpts); changed = true }
                if (changed) mirrorActiveProfile()
            }
            if (changed) {
                if (sync) orchestrator.restartServerIfRunning()
                orchestrator.restartProxyIfRunning()
            }
        }
    }

    // --- Updates ---
    fun checkForUpdate() {
        viewModelScope.launch { appUpdater.checkForUpdate(silent = false) }
    }

    fun downloadUpdate() {
        viewModelScope.launch { appUpdater.downloadUpdate() }
    }

    fun installUpdate() {
        appUpdater.installUpdate()
    }

    fun resetUpdateState() {
        appUpdater.resetState()
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            if (ProxyServiceState.isRunning.value) {
                appContext.stopService(Intent(appContext, ProxyService::class.java))
            }
            prefs.resetAll()
            sshRepository.resetAll()
            proxyManager.clearState()
            ProxyServiceState.clearLogs()

            val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                appContext.startActivity(intent)
            }
        }
    }
}
