package com.freeturn.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.UpdateState
import com.freeturn.app.domain.update.AppUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Настройки самого приложения и его обновления. Серверы - [com.freeturn.app.viewmodel.server.ServerConfigViewModel],
 * бэкап и сброс - [BackupViewModel].
 */
class SettingsViewModel(
    private val prefs: AppPreferences,
    private val appUpdater: AppUpdater
) : ViewModel() {

    // Стартовые значения - те же, что дефолты AppPreferences: иначе тумблер моргает
    // до первой эмиссии DataStore.
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val dynamicTheme: StateFlow<Boolean> = prefs.dynamicThemeFlow.state(true)
    val nerdMode: StateFlow<Boolean> = prefs.nerdModeFlow.state(true)
    val privacyMode: StateFlow<Boolean> = prefs.privacyModeFlow.state(false)
    val seasonalDecor: StateFlow<Boolean> = prefs.seasonalDecorFlow.state(true)
    val autoConnect: StateFlow<Boolean> = prefs.autoConnectFlow.state(false)
    val restartServerOnSwitch: StateFlow<Boolean> = prefs.restartServerOnSwitchFlow.state(false)
    val hotspotProxyEnabled: StateFlow<Boolean> = prefs.hotspotProxyEnabledFlow.state(false)
    val suppressUpdatePrompt: StateFlow<Boolean> = prefs.suppressUpdatePromptFlow.state(false)
    val suppressTgPrompt: StateFlow<Boolean> = prefs.suppressTgPromptFlow.state(false)

    val updateState: StateFlow<UpdateState> = appUpdater.state

    private val _showV5SetupNotice = MutableStateFlow(false)
    val showV5SetupNotice: StateFlow<Boolean> = _showV5SetupNotice.asStateFlow()

    fun acknowledgeV5SetupNotice() {
        viewModelScope.launch {
            prefs.acknowledgeV5SetupNotice()
            _showV5SetupNotice.value = false
        }
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _initialTgSubscribeShown = MutableStateFlow(false)
    val initialTgSubscribeShown: StateFlow<Boolean> = _initialTgSubscribeShown.asStateFlow()

    // Снимок не даёт диалогу мигнуть на дефолтном значении до первого emit.
    private val _initialSuppressTgPrompt = MutableStateFlow(false)
    val initialSuppressTgPrompt: StateFlow<Boolean> = _initialSuppressTgPrompt.asStateFlow()

    // Ожидаем DataStore, чтобы дефолт StateFlow не пропустил диалог первой сессии.
    suspend fun batteryPromptShownOnce(): Boolean = prefs.batteryPromptShownFlow.first()

    init {
        viewModelScope.launch {
            _initialTgSubscribeShown.value = prefs.tgSubscribeShownFlow.first()
            _initialSuppressTgPrompt.value = prefs.suppressTgPromptFlow.first()
            _showV5SetupNotice.value = prefs.v5SetupNoticePending()
            _isInitialized.value = true
        }
        viewModelScope.launch {
            appUpdater.checkForUpdate(silent = true)
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setPrivacyMode(enabled) }
    }

    fun setSeasonalDecor(enabled: Boolean) {
        viewModelScope.launch { prefs.setSeasonalDecor(enabled) }
    }

    fun setRestartServerOnSwitch(enabled: Boolean) {
        viewModelScope.launch { prefs.setRestartServerOnSwitch(enabled) }
    }

    // Применяется со следующего запуска: слушающий порт поднимается вместе с сессией.
    fun setHotspotProxyEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setHotspotProxyEnabled(enabled) }
    }

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicTheme(enabled) }
    }

    fun setNerdMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setNerdMode(enabled) }
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

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoConnect(enabled) }
    }

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
}
