package com.freeturn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.LocalProxyManager
import com.freeturn.app.domain.ProxyState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProxyViewModel(
    private val proxyManager: LocalProxyManager,
    private val prefs: AppPreferences
) : ViewModel() {

    val proxyState: StateFlow<ProxyState> = proxyManager.proxyState
    val connectedSince: StateFlow<Long?> = ProxyServiceState.connectedSince
    val logs: StateFlow<List<String>> = ProxyServiceState.logs

    fun startProxy() {
        viewModelScope.launch {
            val config = prefs.clientConfigFlow.first()
            proxyManager.startProxy(config)
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
}
