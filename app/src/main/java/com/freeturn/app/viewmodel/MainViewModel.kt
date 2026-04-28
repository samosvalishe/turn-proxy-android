package com.freeturn.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.ProxyService
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.Profile
import com.freeturn.app.data.ProfilesSnapshot
import com.freeturn.app.data.SshConfig
import java.util.UUID
import com.freeturn.app.domain.AppUpdater
import com.freeturn.app.domain.LocalProxyManager
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val sshRepository = SshRepository()
    private val proxyManager = LocalProxyManager(application)
    private val appUpdater = AppUpdater(application)

    val sshState: StateFlow<SshConnectionState> = sshRepository.sshState
    val serverState: StateFlow<ServerState> = sshRepository.serverState
    val sshLog: StateFlow<List<String>> = sshRepository.sshLog

    val proxyState: StateFlow<ProxyState> = proxyManager.proxyState
    val connectedSince: StateFlow<Long?> = ProxyServiceState.connectedSince
    val logs: StateFlow<List<String>> = ProxyServiceState.logs
    val customKernelExists: StateFlow<Boolean> = proxyManager.customKernelExists
    val updateState: StateFlow<UpdateState> = appUpdater.state

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initialOnboardingDone = MutableStateFlow(false)
    val initialOnboardingDone: StateFlow<Boolean> = _initialOnboardingDone.asStateFlow()

    private val _initialTgSubscribeShown = MutableStateFlow(false)
    val initialTgSubscribeShown: StateFlow<Boolean> = _initialTgSubscribeShown.asStateFlow()

    init {
        viewModelScope.launch {
            val done = prefs.onboardingDoneFlow.first()
            val tgShown = prefs.tgSubscribeShownFlow.first()
            _initialOnboardingDone.value = done
            _initialTgSubscribeShown.value = tgShown
            _isInitialized.value = true
        }
        viewModelScope.launch {
            proxyManager.observeProxyLifecycle()
        }
        viewModelScope.launch {
            proxyManager.observeProxyServiceStatus()
        }
        viewModelScope.launch {
            proxyManager.observeConnectionStats()
        }
        viewModelScope.launch {
            proxyManager.observeCaptchaEvents()
        }
        proxyManager.syncInitialState()

        viewModelScope.launch {
            appUpdater.checkForUpdate(silent = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        proxyManager.destroy()
    }

    val sshConfig: StateFlow<SshConfig> = prefs.sshConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SshConfig())

    val clientConfig: StateFlow<ClientConfig> = prefs.clientConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    val onboardingDone: StateFlow<Boolean> = prefs.onboardingDoneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    /** Сохранить текущие настройки как новый профиль и сделать активным. */
    fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            val current = prefs.profilesSnapshot.first()
            val ssh = prefs.sshConfigFlow.first()
            val client = prefs.clientConfigFlow.first()
            val listen = prefs.proxyListenFlow.first()
            val connect = prefs.proxyConnectFlow.first()
            val base = name.trim().ifBlank { defaultProfileName(client.serverAddress) }
            val profile = Profile(
                id = UUID.randomUUID().toString(),
                name = uniqueProfileName(base, current.list, excludingId = null),
                ssh = ssh,
                client = client,
                proxyListen = listen,
                proxyConnect = connect
            )
            prefs.saveProfiles(current.list + profile)
            prefs.setActiveProfileId(profile.id)
        }
    }

    /**
     * Гарантирует уникальность имени профиля. Защита от deep-link / автоматических
     * сценариев, где UI-диалог не валидирует ввод. Дубль превращается в
     * "имя (2)", "имя (3)" и т.д.
     */
    private fun uniqueProfileName(
        base: String,
        existing: List<Profile>,
        excludingId: String?
    ): String {
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

    /** Публичный триггер пересохранения активного профиля текущими настройками. */
    fun updateActiveProfileFromCurrent() {
        viewModelScope.launch { mirrorActiveProfile() }
    }

    /**
     * Зеркалирует текущие prefs (ssh+client) в активный профиль. Источник истины —
     * профиль, но legacy-ключи остаются «scratch»-областью для случая «нет профилей».
     * Вызывается автоматически после каждой записи ssh/client конфига, чтобы
     * пользовательские правки не терялись при переключении профилей.
     */
    private suspend fun mirrorActiveProfile() {
        val current = prefs.profilesSnapshot.first()
        val activeId = current.activeId ?: return
        val ssh = prefs.sshConfigFlow.first()
        val client = prefs.clientConfigFlow.first()
        val listen = prefs.proxyListenFlow.first()
        val connect = prefs.proxyConnectFlow.first()
        val updated = current.list.map {
            if (it.id == activeId)
                it.copy(ssh = ssh, client = client, proxyListen = listen, proxyConnect = connect)
            else it
        }
        if (updated != current.list) prefs.saveProfiles(updated)
    }

    fun renameProfile(id: String, name: String) {
        viewModelScope.launch {
            val current = prefs.profilesSnapshot.first()
            val target = current.list.firstOrNull { it.id == id } ?: return@launch
            val base = name.trim().ifBlank { target.name }
            val unique = uniqueProfileName(base, current.list, excludingId = id)
            val updated = current.list.map {
                if (it.id == id) it.copy(name = unique) else it
            }
            prefs.saveProfiles(updated)
        }
    }

    /**
     * Применяет профиль: загружает его ssh/client в legacy-ключи (которые читают
     * существующие экраны) и помечает активным. Если прокси запущен — перезапускает,
     * иначе логика клиента продолжит использовать старые настройки.
     */
    fun applyProfile(id: String) {
        viewModelScope.launch {
            val current = prefs.profilesSnapshot.first()
            val target = current.list.firstOrNull { it.id == id } ?: return@launch
            prefs.saveSshConfig(target.ssh)
            prefs.saveClientConfig(target.client)
            prefs.saveProxyConfig(target.proxyListen, target.proxyConnect)
            prefs.setActiveProfileId(target.id)

            if (ProxyServiceState.isRunning.value) {
                proxyManager.stopProxy()
                // Ждём фактической остановки сервиса вместо магического delay,
                // иначе onStartCommand может стартовать со старыми аргументами.
                withTimeoutOrNull(2_000) {
                    ProxyServiceState.isRunning.first { !it }
                }
                proxyManager.startProxy(target.client)
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
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
                }
            }
        }
    }

    private fun defaultProfileName(serverAddr: String): String =
        serverAddr.substringBefore(':').takeIf { it.isNotBlank() }
            ?: getApplication<Application>().getString(com.freeturn.app.R.string.profile_default_name)

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicTheme(enabled) }
    }

    fun setTgSubscribeShown() {
        viewModelScope.launch { prefs.setTgSubscribeShown() }
    }

    private val _privacyMode = MutableStateFlow(false)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) { _privacyMode.value = enabled }

    // SSH
    fun connectSsh(config: SshConfig) {
        viewModelScope.launch {
            prefs.saveSshConfig(config)
            mirrorActiveProfile()
            val (success, fp) = sshRepository.connectSsh(config)
            if (success) {
                if (config.hostFingerprint.isEmpty() && fp != null) {
                    prefs.saveSshFingerprint(fp)
                    mirrorActiveProfile()
                }
                HapticUtil.perform(getApplication(), HapticUtil.Pattern.SUCCESS)
            } else {
                HapticUtil.perform(getApplication(), HapticUtil.Pattern.ERROR)
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

    // Server management
    fun installServer() {
        viewModelScope.launch { sshRepository.installServer() }
    }

    fun startServer() {
        val l = proxyListen.value
        val c = proxyConnect.value
        if (!l.matches(Regex("""^[\w.\-]+:\d{1,5}$""")) || !c.matches(Regex("""^[\w.\-]+:\d{1,5}$"""))) {
            sshRepository.updateServerState(ServerState.Error("Неверный формат адреса (ожидается host:port)"))
            return
        }
        val vless = clientConfig.value.vlessMode
        viewModelScope.launch { sshRepository.startServer(l, c, vless) }
    }

    fun stopServer() {
        viewModelScope.launch { sshRepository.stopServer() }
    }

    /** Переключает VLESS-режим. Если SSH подключён и сервер запущен — автоперезапуск. */
    fun setVlessMode(enabled: Boolean) {
        val current = clientConfig.value
        if (current.vlessMode == enabled) return
        viewModelScope.launch {
            prefs.saveClientConfig(current.copy(vlessMode = enabled))
            mirrorActiveProfile()
            val serverRunning = (serverState.value as? ServerState.Known)?.running == true
            if (serverRunning) {
                sshRepository.stopServer()
                startServer()
            }
        }
    }

    // Local proxy
    fun startProxy() {
        viewModelScope.launch {
            proxyManager.startProxy(clientConfig.value)
        }
    }

    fun stopProxy() {
        proxyManager.stopProxy()
    }

    fun dismissCaptcha() {
        proxyManager.dismissCaptcha()
    }

    fun clearLogs() {
        ProxyServiceState.clearLogs()
    }

    // Preferences
    fun saveClientConfig(config: ClientConfig) {
        viewModelScope.launch {
            prefs.saveClientConfig(config)
            mirrorActiveProfile()
        }
    }

    fun saveProxyServerConfig(listen: String, connect: String) {
        viewModelScope.launch {
            prefs.saveProxyConfig(listen, connect)
            mirrorActiveProfile()
        }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }

    // Custom kernel
    private val _kernelError = MutableStateFlow<String?>(null)
    val kernelError: StateFlow<String?> = _kernelError.asStateFlow()

    fun setCustomKernel(uri: Uri) {
        viewModelScope.launch {
            _kernelError.value = proxyManager.setCustomKernel(uri)
        }
    }

    fun clearCustomKernel() {
        proxyManager.clearCustomKernel()
    }

    fun clearKernelError() {
        _kernelError.value = null
    }

    // App update
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

    fun resetAllSettings(context: Context) {
        viewModelScope.launch {
            if (ProxyServiceState.isRunning.value) {
                context.stopService(Intent(context, ProxyService::class.java))
            }
            prefs.resetAll()
            sshRepository.resetAll()
            proxyManager.clearState()
            ProxyServiceState.clearLogs()

            val intent = (context as? android.app.Activity)?.intent
                ?: Intent(context, com.freeturn.app.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
    }
}
