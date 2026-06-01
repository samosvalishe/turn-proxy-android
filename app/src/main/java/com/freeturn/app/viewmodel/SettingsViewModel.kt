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
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.data.SshConfig
import com.freeturn.app.domain.AppUpdater
import com.freeturn.app.domain.LocalProxyManager
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
    private val context: Context
) : ViewModel() {

    val clientConfig: StateFlow<ClientConfig> = prefs.clientConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    val proxyListen: StateFlow<String> = prefs.proxyListenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0.0.0.0:56000")

    val proxyConnect: StateFlow<String> = prefs.proxyConnectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "127.0.0.1:40537")

    val dynamicTheme: StateFlow<Boolean> = prefs.dynamicThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val tgSubscribeShown: StateFlow<Boolean> = prefs.tgSubscribeShownFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val profilesSnapshot: StateFlow<ProfilesSnapshot> = prefs.profilesSnapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesSnapshot())

    val updateState: StateFlow<UpdateState> = appUpdater.state

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initialOnboardingDone = MutableStateFlow(false)
    val initialOnboardingDone: StateFlow<Boolean> = _initialOnboardingDone.asStateFlow()

    private val _initialTgSubscribeShown = MutableStateFlow(false)
    val initialTgSubscribeShown: StateFlow<Boolean> = _initialTgSubscribeShown.asStateFlow()

    val onboardingDone: StateFlow<Boolean> = prefs.onboardingDoneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    private val profileMutex = Mutex()

    init {
        viewModelScope.launch {
            val done = prefs.onboardingDoneFlow.first()
            val tgShown = prefs.tgSubscribeShownFlow.first()
            _initialOnboardingDone.value = done
            _initialTgSubscribeShown.value = tgShown
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

    fun saveClientConfig(config: ClientConfig) {
        viewModelScope.launch {
            profileMutex.withLock { prefs.saveClientConfig(config) }
        }
    }

    fun saveProxyServerConfig(listen: String, connect: String) {
        viewModelScope.launch {
            profileMutex.withLock { prefs.saveProxyConfig(listen, connect) }
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

    fun updateActiveProfileFromCurrent() {
        viewModelScope.launch { profileMutex.withLock { mirrorActiveProfile() } }
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
            if (serverRunning) restartServerIfRunning()

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

    private fun serverAddrToProfileName(serverAddr: String): String =
        serverAddr.substringBefore(':').takeIf { it.isNotBlank() }
            ?: context.getString(com.freeturn.app.R.string.profile_default_name)

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

    // --- Server & Proxy Switches (TcpForward, Bond, Obf, Sync) ---
    fun setBond(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.clientConfigFlow.first()
            if (current.bond == enabled) return@launch
            val next = current.copy(bond = enabled)
            profileMutex.withLock { prefs.saveClientConfig(next) }
            restartProxyIfRunning()
        }
    }

    fun setObfProfile(profile: String) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            val known = sshRepository.serverState.value as? ServerState.Known
            val storedMatches = current.obfProfile == profile
            val effectiveMatches = known?.obfProfile?.let { it == profile } ?: true
            if (storedMatches && effectiveMatches) return@launch
            val sync = clientConfig.value.syncServerSwitches
            var next = current.copy(obfProfile = profile)
            if (profile != ObfProfile.NONE && next.obfKey.isBlank() && sync) {
                val key = sshRepository.generateObfKey()
                if (!key.isNullOrBlank()) next = next.copy(obfKey = key)
            }
            if (!storedMatches || next.obfKey != current.obfKey) {
                profileMutex.withLock { prefs.saveServerOpts(next) }
            }
            if (sync) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    fun setSyncServerSwitches(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.clientConfigFlow.first()
            if (current.syncServerSwitches == enabled) return@launch
            val next = current.copy(syncServerSwitches = enabled)
            profileMutex.withLock { prefs.saveClientConfig(next) }
            if (enabled) {
                restartServerIfRunning()
                restartProxyIfRunning()
            }
        }
    }

    fun setObfKey(key: String) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            val trimmed = key.trim()
            if (current.obfKey == trimmed) return@launch
            val next = current.copy(obfKey = trimmed)
            profileMutex.withLock { prefs.saveServerOpts(next) }
            if (clientConfig.value.syncServerSwitches) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    fun setTcpForward(enabled: Boolean) {
        val current = clientConfig.value
        val known = sshRepository.serverState.value as? ServerState.Known
        val storedMatches = current.tcpForward == enabled
        val effectiveMatches = known?.tcpMode?.let { it == enabled } ?: true
        if (storedMatches && effectiveMatches) return
        viewModelScope.launch {
            if (!storedMatches) {
                val next = current.copy(tcpForward = enabled)
                profileMutex.withLock { prefs.saveClientConfig(next) }
            }
            if (current.syncServerSwitches) {
                val serverRunning = (sshRepository.serverState.value as? ServerState.Known)?.running == true
                if (serverRunning) {
                    sshRepository.stopServer()
                    startServerFromPrefs()
                }
            }
            restartProxyIfRunning()
        }
    }

    private suspend fun startServerFromPrefs() {
        val l = prefs.proxyListenFlow.first()
        val c = prefs.proxyConnectFlow.first()
        val tcpMode = prefs.clientConfigFlow.first().tcpForward
        val opts = prefs.serverOptsFlow.first()
        sshRepository.startServer(
            listen = l, connect = c,
            tcpMode = tcpMode,
            obfProfile = if (opts.obfEnabled) opts.obfProfile else "none",
            obfKey = if (opts.obfEnabled) opts.obfKey else ""
        )
    }

    private suspend fun restartServerIfRunning() {
        val running = (sshRepository.serverState.value as? ServerState.Known)?.running == true
        if (!running) return
        val l = prefs.proxyListenFlow.first()
        val c = prefs.proxyConnectFlow.first()
        if (!l.matches(Regex("""^[\w.\-]+:\d{1,5}$""")) ||
            !c.matches(Regex("""^[\w.\-]+:\d{1,5}$"""))) return
        val opts = prefs.serverOptsFlow.first()
        val tcpMode = prefs.clientConfigFlow.first().tcpForward
        sshRepository.stopServer()
        sshRepository.startServer(
            listen = l, connect = c,
            tcpMode = tcpMode,
            obfProfile = if (opts.obfEnabled) opts.obfProfile else "none",
            obfKey = if (opts.obfEnabled) opts.obfKey else ""
        )
    }

    private suspend fun restartProxyIfRunning() {
        if (!ProxyServiceState.isRunning.value) return
        proxyManager.stopProxy()
        withTimeoutOrNull(2_000) {
            ProxyServiceState.isRunning.first { !it }
        }
        proxyManager.startProxy(prefs.clientConfigFlow.first())
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
                context.stopService(Intent(context, ProxyService::class.java))
            }
            prefs.resetAll()
            sshRepository.resetAll()
            proxyManager.clearState()
            ProxyServiceState.clearLogs()

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
            }
        }
    }
}
