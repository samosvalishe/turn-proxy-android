package com.freeturn.app.domain

import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class ProxyOrchestrator(
    private val prefs: AppPreferences,
    private val proxyManager: LocalProxyManager,
    private val sshRepository: SshRepository
) {
    suspend fun restartServerIfRunning() {
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
            obfKey = if (opts.obfEnabled) opts.obfKey else "",
            clientId = prefs.ownClientId()
        )
    }

    suspend fun restartProxyIfRunning() {
        if (!ProxyServiceState.isRunning.value) return
        proxyManager.stopProxy()
        withTimeoutOrNull(2_000) {
            ProxyServiceState.isRunning.first { !it }
        }
        proxyManager.startProxy(prefs.clientConfigFlow.first())
    }

}
