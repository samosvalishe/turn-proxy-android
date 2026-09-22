package com.freeturn.app.viewmodel.proxy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.proxy.LogEntry
import com.freeturn.app.domain.proxy.ProxyEngine
import com.freeturn.app.domain.proxy.ProxyLog
import com.freeturn.app.domain.proxy.ProxyPhase
import com.freeturn.app.domain.proxy.ProxyServiceLauncher
import com.freeturn.app.domain.proxy.ProxyStatus
import com.freeturn.app.domain.proxy.ProxyStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ProxyViewModel(
    private val launcher: ProxyServiceLauncher,
    private val prefs: AppPreferences,
    private val engine: ProxyEngine,
    private val store: ProxyStore,
    private val log: ProxyLog
) : ViewModel() {

    val status: StateFlow<ProxyStatus> = store.status
    val logs: StateFlow<List<LogEntry>> = log.lines

    fun start() {
        launcher.start()
    }

    fun stop() {
        launcher.stop()
    }

    /**
     * Возврат приложения на передний план. Живую сессию не трогаем - открытие окна
     * не событие сети, а пинок ядру рециклил бы рабочие аллокации. Мёртвую поднимаем
     * только с явного согласия ([AppPreferences.autoConnectFlow]): намерение переживает
     * смерть процесса и перезагрузку, поэтому без настройки открытие приложения
     * поднимало VPN само и отбирало tun у чужого.
     *
     * [vpnConsent] - проверка согласия на VpnService, зовётся последней: сам
     * `VpnService.prepare()` отзывает разрешение у активного чужого VPN. Без согласия
     * в WG-режиме стартовать нечем, а спрашивать молча, без действия пользователя, нельзя.
     */
    fun onForeground(vpnConsent: () -> Boolean) {
        if (store.status.value.phase != ProxyPhase.Idle) return
        viewModelScope.launch {
            if (!prefs.autoConnectFlow.first()) return@launch
            if (!prefs.proxyDesiredFlow.first()) return@launch
            val tunnel = prefs.clientConfigFlow.first().wireGuardActive
            // Sticky-старт мог занять сессию, пока читали DataStore.
            if (engine.isRunning || store.status.value.phase != ProxyPhase.Idle) return@launch
            if (tunnel && !vpnConsent()) return@launch
            launcher.start()
        }
    }

    /** Окно закрыл пользователь; ядро ждёт решения дальше и выдаст новую капчу. */
    fun dismissCaptcha() = store.setCaptcha("")

    /** С экрана чистим и файл: сохранять историю против воли пользователя незачем. */
    fun clearLogs() = log.clearAll()

    /** false - отправлять нечего. */
    suspend fun exportLogs(target: File): Boolean = log.export(target)
}
