package com.freeturn.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val sshRepository = SshRepository(application)
    private val proxyManager = LocalProxyManager(application)
    private val appUpdater = AppUpdater(application)

    val sshState: StateFlow<SshConnectionState> = sshRepository.sshState
    val serverState: StateFlow<ServerState> = sshRepository.serverState
    val sshLog: StateFlow<List<String>> = sshRepository.sshLog
    val serverLogs: StateFlow<String?> = sshRepository.serverLogs

    val proxyState: StateFlow<ProxyState> = proxyManager.proxyState
    val connectedSince: StateFlow<Long?> = ProxyServiceState.connectedSince
    val logs: StateFlow<List<String>> = ProxyServiceState.logs
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

    /**
     * Сериализует все операции над списком профилей и зеркалирование активного
     * профиля. Без этого параллельные viewModelScope.launch'и могли прочитать
     * устаревший snapshot и затереть/перетянуть данные между профилями.
     */
    private val profileMutex = Mutex()

    /** Сохранить текущие настройки как новый профиль и сделать активным. */
    fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                val current = prefs.profilesSnapshot.first()
                val ssh = prefs.sshConfigFlow.first()
                val client = prefs.clientConfigFlow.first()
                val listen = prefs.proxyListenFlow.first()
                val connect = prefs.proxyConnectFlow.first()
                val server = prefs.serverOptsFlow.first()
                val base = name.trim().ifBlank { defaultProfileName(client.serverAddress) }
                val profile = Profile(
                    id = UUID.randomUUID().toString(),
                    name = uniqueProfileName(base, current.list, excludingId = null),
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
        viewModelScope.launch { profileMutex.withLock { mirrorActiveProfile() } }
    }

    /**
     * Зеркалирует текущие prefs (ssh+client+server) в активный профиль. Источник истины —
     * профиль, но legacy-ключи остаются «scratch»-областью для случая «нет профилей».
     * Вызывается автоматически после каждой записи ssh/client/server конфига, чтобы
     * пользовательские правки не терялись при переключении профилей.
     *
     * Параметры `*Override` позволяют передать значение, которое мы только что
     * записали в DataStore, не полагаясь на асинхронную эмиссию Flow.first() —
     * иначе возможен race, когда .first() возвращает доcоставочный snapshot.
     *
     * ВАЖНО: вызывать только под profileMutex.
     */
    private suspend fun mirrorActiveProfile(
        sshOverride: SshConfig? = null,
        clientOverride: ClientConfig? = null,
        listenOverride: String? = null,
        connectOverride: String? = null,
        serverOverride: AppPreferences.ServerOpts? = null,
    ) {
        val current = prefs.profilesSnapshot.first()
        val activeId = current.activeId ?: return
        val ssh = sshOverride ?: prefs.sshConfigFlow.first()
        val client = clientOverride ?: prefs.clientConfigFlow.first()
        val listen = listenOverride ?: prefs.proxyListenFlow.first()
        val connect = connectOverride ?: prefs.proxyConnectFlow.first()
        val server = serverOverride ?: prefs.serverOptsFlow.first()
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
                val unique = uniqueProfileName(base, current.list, excludingId = id)
                val updated = current.list.map {
                    if (it.id == id) it.copy(name = unique) else it
                }
                prefs.saveProfiles(updated)
            }
        }
    }

    /**
     * Применяет профиль: загружает его ssh/client/server в legacy-ключи и помечает
     * активным. Если прокси/сервер запущены — перезапускает.
     */
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

            // Рестарт вне лока, чтобы не блокировать другие профильные операции
            // на время stop/start (может занять секунды).
            val serverRunning = (serverState.value as? ServerState.Known)?.running == true
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
            profileMutex.withLock {
                prefs.saveSshConfig(config)
                mirrorActiveProfile(sshOverride = config)
            }
            val (success, fp) = sshRepository.connectSsh(config)
            if (success) {
                if (config.hostFingerprint.isEmpty() && fp != null) {
                    profileMutex.withLock {
                        prefs.saveSshFingerprint(fp)
                        mirrorActiveProfile(sshOverride = config.copy(hostFingerprint = fp))
                    }
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
    val serverOpts: StateFlow<AppPreferences.ServerOpts> = prefs.serverOptsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.ServerOpts())

    private val _serverInstallStage = MutableStateFlow<String?>(null)
    /** Последний результат install: "cached" | "downloaded" | null. */
    val serverInstallStage: StateFlow<String?> = _serverInstallStage.asStateFlow()

    private val _isRegeneratingWrapKey = MutableStateFlow(false)
    /** true — пока крутится gen-wrap-key + последующий рестарт сервера/клиента. */
    val isRegeneratingWrapKey: StateFlow<Boolean> = _isRegeneratingWrapKey.asStateFlow()

    fun installServer() {
        viewModelScope.launch {
            _serverInstallStage.value = null
            val outcome = sshRepository.installServer()
            if (outcome is com.freeturn.app.domain.InstallOutcome.Success) {
                _serverInstallStage.value = outcome.stage
                // Авто-генерация wrap-key после первого скачивания, если ещё нет.
                if (outcome.stage == "downloaded") {
                    val current = prefs.serverOptsFlow.first()
                    if (current.wrapKey.isBlank()) {
                        val key = sshRepository.generateWrapKey()
                        if (!key.isNullOrBlank()) {
                            val next = current.copy(wrapKey = key)
                            profileMutex.withLock {
                                prefs.saveServerOpts(next)
                                mirrorActiveProfile(serverOverride = next)
                            }
                        }
                    }
                }
                // Скрипт переустановил бинарь поверх работающего процесса —
                // SshRepository уже вызвал stop, осталось стартануть с актуальными
                // listen/connect и serverOpts (их знает только VM).
                if (outcome.needsRestart) {
                    startServer()
                    restartProxyIfRunning()
                }
            }
        }
    }

    fun consumeInstallStage() { _serverInstallStage.value = null }

    fun startServer() {
        val l = proxyListen.value
        val c = proxyConnect.value
        if (!l.matches(Regex("""^[\w.\-]+:\d{1,5}$""")) || !c.matches(Regex("""^[\w.\-]+:\d{1,5}$"""))) {
            sshRepository.updateServerState(ServerState.Error("Неверный формат адреса (ожидается host:port)"))
            return
        }
        val vless = clientConfig.value.vlessMode
        val opts = serverOpts.value
        val wrapKey = if (opts.wrapEnabled) opts.wrapKey else ""
        viewModelScope.launch {
            sshRepository.startServer(
                listen = l, connect = c,
                vlessMode = vless,
                vlessBond = opts.vlessBond,
                wrapKey = wrapKey,
                kcpFec = opts.kcpFec
            )
        }
    }

    fun setServerKcpFec(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            if (current.kcpFec == enabled) return@launch
            val next = current.copy(kcpFec = enabled)
            profileMutex.withLock {
                prefs.saveServerOpts(next)
                mirrorActiveProfile(serverOverride = next)
            }
            if (clientConfig.value.syncServerSwitches) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    fun setServerVlessBond(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            if (current.vlessBond == enabled) return@launch
            val next = current.copy(vlessBond = enabled)
            profileMutex.withLock {
                prefs.saveServerOpts(next)
                mirrorActiveProfile(serverOverride = next)
            }
            if (clientConfig.value.syncServerSwitches) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    fun setServerWrapEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            if (current.wrapEnabled == enabled) return@launch
            val sync = clientConfig.value.syncServerSwitches
            var next = current.copy(wrapEnabled = enabled)
            // При первом включении wrap, если ключа ещё нет и sync включён —
            // пробуем взять с сервера. В !sync режиме сервер не трогаем.
            if (enabled && next.wrapKey.isBlank() && sync) {
                val key = sshRepository.generateWrapKey()
                if (!key.isNullOrBlank()) next = next.copy(wrapKey = key)
            }
            profileMutex.withLock {
                prefs.saveServerOpts(next)
                mirrorActiveProfile(serverOverride = next)
            }
            if (sync) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    /**
     * Глобальный sync-флаг. При включении (false→true) выравниваем сервер с
     * клиентскими свитчами разовым рестартом, иначе разъехавшиеся ключ/флаги
     * провалят DTLS handshake.
     */
    fun setSyncServerSwitches(enabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.clientConfigFlow.first()
            if (current.syncServerSwitches == enabled) return@launch
            val next = current.copy(syncServerSwitches = enabled)
            profileMutex.withLock {
                prefs.saveClientConfig(next)
                mirrorActiveProfile(clientOverride = next)
            }
            if (enabled) {
                restartServerIfRunning()
                restartProxyIfRunning()
            }
        }
    }

    /**
     * Прямое редактирование wrap-key пользователем. Принимает любую строку
     * (валидация формата — на стороне ProxyService/скрипта); если ключ
     * меняется в sync-режиме, перезапускаем сервер.
     */
    fun setWrapKey(key: String) {
        viewModelScope.launch {
            val current = prefs.serverOptsFlow.first()
            val trimmed = key.trim()
            if (current.wrapKey == trimmed) return@launch
            val next = current.copy(wrapKey = trimmed)
            profileMutex.withLock {
                prefs.saveServerOpts(next)
                mirrorActiveProfile(serverOverride = next)
            }
            if (clientConfig.value.syncServerSwitches) restartServerIfRunning()
            restartProxyIfRunning()
        }
    }

    fun regenerateWrapKey() {
        viewModelScope.launch {
            if (_isRegeneratingWrapKey.value) return@launch
            _isRegeneratingWrapKey.value = true
            try {
                val key = sshRepository.generateWrapKey() ?: return@launch
                val current = prefs.serverOptsFlow.first()
                val next = current.copy(wrapKey = key)
                profileMutex.withLock {
                    prefs.saveServerOpts(next)
                    mirrorActiveProfile(serverOverride = next)
                }
                if (clientConfig.value.syncServerSwitches) restartServerIfRunning()
                restartProxyIfRunning()
            } finally {
                _isRegeneratingWrapKey.value = false
            }
        }
    }

    /** Перезапускает сервер с актуальными опциями, если он сейчас запущен. */
    private suspend fun restartServerIfRunning() {
        val running = (serverState.value as? ServerState.Known)?.running == true
        if (!running) return
        val l = proxyListen.value
        val c = proxyConnect.value
        if (!l.matches(Regex("""^[\w.\-]+:\d{1,5}$""")) ||
            !c.matches(Regex("""^[\w.\-]+:\d{1,5}$"""))) return
        val opts = prefs.serverOptsFlow.first()
        val vless = clientConfig.value.vlessMode
        sshRepository.stopServer()
        sshRepository.startServer(
            listen = l, connect = c,
            vlessMode = vless,
            vlessBond = opts.vlessBond,
            wrapKey = if (opts.wrapEnabled) opts.wrapKey else "",
            kcpFec = opts.kcpFec
        )
    }

    /**
     * Перезапускает локальный клиент с актуальным ClientConfig + serverOpts,
     * если он сейчас работает. Используется после изменения sync-флагов
     * (vlessMode/vlessBond/wrap*), чтобы клиент и сервер не разъехались по
     * параметрам после отдельного рестарта сервера.
     */
    private suspend fun restartProxyIfRunning() {
        if (!ProxyServiceState.isRunning.value) return
        proxyManager.stopProxy()
        withTimeoutOrNull(2_000) {
            ProxyServiceState.isRunning.first { !it }
        }
        proxyManager.startProxy(clientConfig.value)
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

    /** Переключает VLESS-режим. Если sync ON и сервер запущен — автоперезапуск. */
    fun setVlessMode(enabled: Boolean) {
        val current = clientConfig.value
        if (current.vlessMode == enabled) return
        viewModelScope.launch {
            val next = current.copy(vlessMode = enabled)
            profileMutex.withLock {
                prefs.saveClientConfig(next)
                mirrorActiveProfile(clientOverride = next)
            }
            if (current.syncServerSwitches) {
                val serverRunning = (serverState.value as? ServerState.Known)?.running == true
                if (serverRunning) {
                    sshRepository.stopServer()
                    startServer()
                }
            }
            restartProxyIfRunning()
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
            profileMutex.withLock {
                prefs.saveClientConfig(config)
                mirrorActiveProfile(clientOverride = config)
            }
        }
    }

    fun saveProxyServerConfig(listen: String, connect: String) {
        viewModelScope.launch {
            profileMutex.withLock {
                prefs.saveProxyConfig(listen, connect)
                mirrorActiveProfile(listenOverride = listen, connectOverride = connect)
            }
        }
    }

    fun setOnboardingDone() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
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
