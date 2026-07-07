package com.freeturn.app.viewmodel.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.backup.BackupCrypto
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.control.UninstallData
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServersSnapshot
import com.freeturn.app.domain.backup.BackupManager
import com.freeturn.app.domain.update.AppUpdater
import com.freeturn.app.domain.proxy.LocalProxyManager
import com.freeturn.app.domain.proxy.ProxyOrchestrator
import com.freeturn.app.domain.server.ServerSetupRepository
import com.freeturn.app.domain.ssh.SshRepository
import com.freeturn.app.viewmodel.uiError
import com.freeturn.app.domain.UpdateState
import com.freeturn.app.domain.proxy.ProxyServiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Состояние очистки сервера от FreeTurn (snapshot для диалога). */
sealed interface ServerCleanupState {
    data object Idle : ServerCleanupState
    data object Running : ServerCleanupState
    data class Done(val data: UninstallData) : ServerCleanupState
    data class Error(val message: String) : ServerCleanupState
}

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val proxyManager: LocalProxyManager,
    private val sshRepository: SshRepository,
    private val serverSetup: ServerSetupRepository,
    private val appUpdater: AppUpdater,
    private val orchestrator: ProxyOrchestrator,
    private val backupManager: BackupManager,
    context: Context
) : ViewModel() {

    // ViewModel переживает пересоздание Activity - храним applicationContext,
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

    val nerdMode: StateFlow<Boolean> = prefs.nerdModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hotspotProxyEnabled: StateFlow<Boolean> = prefs.hotspotProxyEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val serversSnapshot: StateFlow<ServersSnapshot> = prefs.serversSnapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServersSnapshot())

    val updateState: StateFlow<UpdateState> = appUpdater.state

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initialTgSubscribeShown = MutableStateFlow(false)
    val initialTgSubscribeShown: StateFlow<Boolean> = _initialTgSubscribeShown.asStateFlow()

    // Снимок на старте (как initialTgSubscribeShown): гейт диалога читается один раз при
    // построении UI - живой StateFlow с дефолтом false мигнул бы диалогом до первого emit.
    private val _initialSuppressTgPrompt = MutableStateFlow(false)
    val initialSuppressTgPrompt: StateFlow<Boolean> = _initialSuppressTgPrompt.asStateFlow()

    val suppressUpdatePrompt: StateFlow<Boolean> = prefs.suppressUpdatePromptFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val suppressTgPrompt: StateFlow<Boolean> = prefs.suppressTgPromptFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val privacyMode: StateFlow<Boolean> = prefs.privacyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val restartServerOnSwitch: StateFlow<Boolean> = prefs.restartServerOnSwitchFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Не StateFlow: .value мог бы вернуть дефолт до первого emit DataStore и скипнуть
    // диалог в первую сессию - тут ждём реальное значение.
    suspend fun batteryPromptShownOnce(): Boolean = prefs.batteryPromptShownFlow.first()

    // Дебаунс быстрых тыков тоггла синка: persist мгновенный, а дорогой сетевой
    // side-effect (SSH stop+start + рестарт прокси) откладывается и коалесцируется.
    private val syncSideEffectMutex = Mutex()
    private var syncSideEffectJob: Job? = null
    private val syncSideEffectDebounceMs = 600L

    init {
        viewModelScope.launch {
            _initialTgSubscribeShown.value = prefs.tgSubscribeShownFlow.first()
            _initialSuppressTgPrompt.value = prefs.suppressTgPromptFlow.first()
            // Восстанавливаем сохранённое состояние тоггла логов при старте.
            ProxyServiceState.setLogsEnabled(prefs.clientConfigFlow.first().logsEnabled)
            _isInitialized.value = true
        }
        viewModelScope.launch {
            appUpdater.checkForUpdate(silent = true)
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setPrivacyMode(enabled) }
    }

    fun setRestartServerOnSwitch(enabled: Boolean) {
        viewModelScope.launch { prefs.setRestartServerOnSwitch(enabled) }
    }

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicTheme(enabled) }
    }

    fun setNerdMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setNerdMode(enabled) }
    }

    fun setHotspotProxyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHotspotProxyEnabled(enabled) }
    }

    fun setTgSubscribeShown() {
        viewModelScope.launch { prefs.setTgSubscribeShown() }
    }

    fun setBatteryPromptShown() {
        viewModelScope.launch { prefs.setBatteryPromptShown() }
    }

    fun setSuppressUpdatePrompt(enabled: Boolean) {
        viewModelScope.launch { prefs.setSuppressUpdatePrompt(enabled) }
    }

    fun setSuppressTgPrompt(enabled: Boolean) {
        viewModelScope.launch { prefs.setSuppressTgPrompt(enabled) }
    }

    /**
     * Сохраняет client-конфиг сервера, который редактировал экран. expectedActiveId -
     * id на момент начала правки (null - активный сейчас): дебаунс-сейв после смены
     * активного уходит в свой сервер, а не затирает новый активный.
     */
    fun saveClientConfig(config: ClientConfig, expectedActiveId: String? = null) {
        viewModelScope.launch {
            val targetId = expectedActiveId
                ?: prefs.serversSnapshot.first().activeId ?: return@launch
            if (!prefs.updateServer(targetId) { it.copy(client = config) }) return@launch
            if (targetId == prefs.serversSnapshot.first().activeId) {
                ProxyServiceState.setLogsEnabled(config.logsEnabled)
            }
        }
    }

    fun setSplitTunnelMode(value: String) {
        viewModelScope.launch {
            prefs.updateActiveServer {
                it.copy(client = it.client.copy(splitTunnelMode = value))
            }
        }
    }

    fun setSplitTunnelApps(value: String) {
        viewModelScope.launch {
            val trimmed = value.trim()
            prefs.updateActiveServer {
                it.copy(client = it.client.copy(splitTunnelApps = trimmed))
            }
        }
    }

    // --- Servers ---

    /**
     * Создаёт пустой сервер (ручная настройка): только имя, остальное - дефолты.
     * Не активирует (кроме первого - правило addServer): пустая запись не должна
     * перебивать рабочий активный сервер. [onAdded] получает id для перехода в хаб.
     * Sync OFF: без SSH пушить нечего, а sync ON прятал бы "Настройки сервера"
     * (serverSettingsAvailable) - пользователь не смог бы донастроить сервер.
     */
    fun addManualServer(name: String, onAdded: (String) -> Unit) {
        viewModelScope.launch {
            val server = Server(name = name, client = ClientConfig(syncServerSwitches = false))
            onAdded(prefs.addServer(server))
        }
    }

    fun renameServer(id: String, name: String) {
        viewModelScope.launch { prefs.renameServer(id, name) }
    }

    /** Клонирует сервер; [onCloned] получает id копии для перехода в её хаб. */
    fun cloneServer(id: String, onCloned: (String) -> Unit) {
        viewModelScope.launch { prefs.cloneServer(id)?.let(onCloned) }
    }

    fun applyServer(id: String) {
        viewModelScope.launch {
            val target = prefs.serversSnapshot.first().list.firstOrNull { it.id == id }
                ?: return@launch
            prefs.setActiveServerId(target.id)

            // Сервер - до прокси: no-op, если сервер не запущен или SSH на другом хосте.
            if (prefs.restartServerOnSwitchFlow.first()) {
                orchestrator.restartServerIfRunning()
            }

            sshRepository.activeSshConfig?.let { prev ->
                if (prev.ip != target.ssh.ip || prev.port != target.ssh.port) {
                    sshRepository.disconnect()
                }
            }

            orchestrator.restartProxyIfRunning()
        }
    }

    private val _cleanupState = MutableStateFlow<ServerCleanupState>(ServerCleanupState.Idle)
    /** Прогресс очистки сервера от FreeTurn (диалог в разделе "Управление"). */
    val cleanupState: StateFlow<ServerCleanupState> = _cleanupState.asStateFlow()

    fun resetCleanupState() { _cleanupState.value = ServerCleanupState.Idle }

    /** Удаление сервера из приложения (локально, без серверной очистки). */
    fun deleteServer(id: String) {
        viewModelScope.launch { prefs.deleteServer(id) }
    }

    /**
     * Снос free-turn-proxy с удалённого сервера по SSH. Сервер из приложения НЕ
     * удаляет - это отдельное действие. Результат/ошибка - в [cleanupState].
     */
    fun cleanupServer(id: String) {
        viewModelScope.launch {
            val cfg = serversSnapshot.value.list.firstOrNull { it.id == id }?.ssh ?: return@launch
            _cleanupState.value = ServerCleanupState.Running
            // Деструктив под верной эскалацией: сохранённый rootMode мог устареть/быть
            // дефолтным ROOT (сервер ни разу не подключали) -> preflight перед сносом.
            val mode = serverSetup.detectRootMode(cfg) ?: cfg.rootMode
            serverSetup.uninstall(cfg.copy(rootMode = mode), withWgPkg = true)
                .onSuccess { _cleanupState.value = ServerCleanupState.Done(it) }
                .onFailure { _cleanupState.value = ServerCleanupState.Error(it.uiError(appContext)) }
        }
    }

    // Правит client-конфиг сервера по id, НЕ требуя его активации. Для активного
    // дополнительно обновляет глобальный флаг логов (его читает рантайм).
    fun updateServerClient(id: String, transform: (ClientConfig) -> ClientConfig) {
        viewModelScope.launch {
            if (!prefs.updateServer(id) { it.copy(client = transform(it.client)) }) return@launch
            val snap = prefs.serversSnapshot.first()
            snap.active?.takeIf { it.id == id }?.let {
                ProxyServiceState.setLogsEnabled(it.client.logsEnabled)
            }
        }
    }

    // --- Server & Proxy Switches (TcpForward, Bond, Obf, Sync) ---
    fun setBond(enabled: Boolean) {
        viewModelScope.launch {
            val changed = prefs.updateActiveServer {
                it.copy(client = it.client.copy(bond = enabled))
            }
            if (changed) orchestrator.restartProxyIfRunning()
        }
    }

    // UI пускает сюда только при остановленном прокси - рестарт не нужен.
    fun setActiveVkLink(link: String) {
        viewModelScope.launch {
            prefs.updateActiveServer {
                it.copy(client = it.client.copy(vkLink = link.trim()))
            }
        }
    }

    fun setSyncServerSwitches(enabled: Boolean) {
        viewModelScope.launch {
            val changed = prefs.updateActiveServer {
                it.copy(client = it.client.copy(syncServerSwitches = enabled))
            }
            if (!changed) return@launch
            // Дебаунс: отменяем отложенный side-effect, перепланируем. delay - окно
            // отмены (гасит спам ON/OFF/ON -> один рестарт). Сам рестарт под mutex
            // (две последовательности не интерливятся) и NonCancellable (новый тык не
            // рвёт SSH-команду на полпути). Читаем ФИНАЛЬНОЕ состояние после дебаунса -
            // рестартим только если синк в итоге включён (...->OFF -> ноль рестартов).
            // Смерть процесса в окне дебаунса оставит сервер на старом конфиге при
            // новом тоггле - принято: probe хаба покажет live-режим, любой следующий
            // apply/старт приводит сервер в соответствие.
            syncSideEffectJob?.cancel()
            syncSideEffectJob = viewModelScope.launch {
                delay(syncSideEffectDebounceMs)
                syncSideEffectMutex.withLock {
                    if (!prefs.clientConfigFlow.first().syncServerSwitches) return@withLock
                    withContext(NonCancellable) {
                        orchestrator.restartServerIfRunning()
                        orchestrator.restartProxyIfRunning()
                    }
                }
            }
        }
    }

    /**
     * Атомарно применяет конфиг сервера (proxy-адреса + tcp-проброс + профиль обфускации
     * + obf-ключ) одной транзакцией и перезапускает сервер/прокси РОВНО один раз - для
     * apply-модели "Настроек сервера" (натыкали черновик -> Применить). Если включается
     * обфускация без ключа, ключ генерируется локально.
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
            val changed = prefs.updateActiveServer { s ->
                val effKey = trimmedKey.ifBlank {
                    s.opts.obfKey.ifBlank {
                        if (obfProfile != ObfProfile.NONE) ObfProfile.generateKey() else ""
                    }
                }
                s.copy(
                    proxyListen = listen,
                    proxyConnect = connect,
                    client = s.client.copy(tcpForward = tcpForward),
                    opts = s.opts.copy(obfProfile = obfProfile, obfKey = effKey)
                )
            }
            if (changed) {
                if (sync) {
                    orchestrator.restartServerIfRunning()
                } else {
                    sshRepository.logNote("рестарт сервера пропущен: синхронизация выключена")
                }
                orchestrator.restartProxyIfRunning()
            }
        }
    }

    /**
     * Apply-модель "Настроек сервера" для НЕактивного сервера (sync OFF): серверные
     * параметры тут клиент-локальны, поэтому пишем только снимок сервера по id -
     * рантайм (SSH/прокси активного сервера) не трогаем. Пустой obf-ключ при
     * включённой обфускации генерируется локально.
     */
    fun updateServerConfig(
        id: String,
        listen: String,
        connect: String,
        tcpForward: Boolean,
        obfProfile: String,
        obfKey: String
    ) {
        viewModelScope.launch {
            prefs.updateServer(id) { target ->
                val effKey = obfKey.trim().ifBlank {
                    target.opts.obfKey.ifBlank {
                        if (obfProfile != ObfProfile.NONE) ObfProfile.generateKey() else ""
                    }
                }
                target.copy(
                    proxyListen = listen,
                    proxyConnect = connect,
                    client = target.client.copy(tcpForward = tcpForward),
                    opts = target.opts.copy(obfProfile = obfProfile, obfKey = effKey)
                )
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

    // --- Экспорт / импорт ---
    // Одноразовые события для снекбара (буфер 1 - не теряем при отсутствии подписчика).
    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents: SharedFlow<BackupEvent> = _backupEvents.asSharedFlow()

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            val event = try {
                val bytes = backupManager.export(password)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IOException("no output stream")
                }
                BackupEvent.ExportSuccess
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupEvent.ExportFailed
            }
            _backupEvents.emit(event)
        }
    }

    fun importBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            val event = try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("no input stream")
                }
                BackupEvent.ImportSuccess(backupManager.import(bytes, password))
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupCrypto.BadPasswordException) {
                BackupEvent.ImportFailed(BackupFailReason.BAD_PASSWORD)
            } catch (_: BackupCrypto.FormatException) {
                BackupEvent.ImportFailed(BackupFailReason.BAD_FILE)
            } catch (_: Exception) {
                BackupEvent.ImportFailed(BackupFailReason.IO)
            }
            _backupEvents.emit(event)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            if (ProxyServiceState.isRunning.value) {
                proxyManager.stopProxy()
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

/** Причина провала импорта - текст для UI выбирает экран. */
enum class BackupFailReason { BAD_PASSWORD, BAD_FILE, IO }

/** Одноразовые результаты экспорта/импорта для снекбара. */
sealed interface BackupEvent {
    data object ExportSuccess : BackupEvent
    data object ExportFailed : BackupEvent
    data class ImportSuccess(val count: Int) : BackupEvent
    data class ImportFailed(val reason: BackupFailReason) : BackupEvent
}
