package com.freeturn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.domain.LocalProxyManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProxyViewModel(
    private val proxyManager: LocalProxyManager,
    private val prefs: AppPreferences
) : ViewModel() {

    val proxyState: StateFlow<ProxyState> = proxyManager.proxyState
    val connectedSince: StateFlow<Long?> = ProxyServiceState.connectedSince
    val logs: StateFlow<List<String>> = ProxyServiceState.logs

    val clientConfig: StateFlow<ClientConfig> = prefs.clientConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientConfig())

    val proxyListen: StateFlow<String> = prefs.proxyListenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0.0.0.0:56000")

    val proxyConnect: StateFlow<String> = prefs.proxyConnectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "127.0.0.1:40537")

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
}
