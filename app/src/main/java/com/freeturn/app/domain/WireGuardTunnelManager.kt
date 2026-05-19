package com.freeturn.app.domain

import android.content.Context
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.TunnelTransport
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class WireGuardTunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val backend by lazy { GoBackend(appContext) }
    private val tunnelRef = AtomicReference<NamedTunnel?>(null)

    suspend fun startAfterProxyReady(cfg: ClientConfig) {
        if (cfg.tunnelTransport != TunnelTransport.WIREGUARD) return
        val rawConfig = cfg.wireGuardConfig.trim()
        if (rawConfig.isBlank()) {
            ProxyServiceState.addLog("WireGuard: конфиг пуст, запуск пропущен")
            return
        }

        val name = cfg.wireGuardTunnelName.trim().ifBlank { "freeturn-wg" }
        val endpoint = cfg.localPort.trim()
        val preparedConfig = rawConfig
            .withLocalEndpoint(endpoint)
            .excludingApp(appContext.packageName)
        val config = Config.parse(
            ByteArrayInputStream(preparedConfig.toByteArray(StandardCharsets.UTF_8))
        )

        stop()
        val tunnel = NamedTunnel(name)
        backend.setState(tunnel, Tunnel.State.UP, config)
        tunnelRef.set(tunnel)
        ProxyServiceState.addLog("WireGuard: туннель $name поднят через $endpoint")
    }

    suspend fun stop() {
        val tunnel = tunnelRef.getAndSet(null) ?: return
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
            ProxyServiceState.addLog("WireGuard: туннель ${tunnel.name} остановлен")
        } catch (e: Exception) {
            ProxyServiceState.addLog("WireGuard: ошибка остановки ${tunnel.name}: ${e.message}")
        }
    }

    private class NamedTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName

        override fun onStateChange(newState: Tunnel.State) {
            ProxyServiceState.addLog("WireGuard: состояние $tunnelName -> $newState")
        }
    }
}

private fun String.withLocalEndpoint(endpoint: String): String {
    if (endpoint.isBlank()) return this
    var inPeer = false
    var replaced = false
    val lines = lineSequence().map { line ->
        val section = line.trim()
        if (section.startsWith("[") && section.endsWith("]")) {
            inPeer = section.equals("[Peer]", ignoreCase = true)
        }
        if (inPeer && section.startsWith("Endpoint", ignoreCase = true) && section.contains("=")) {
            replaced = true
            "Endpoint = $endpoint"
        } else {
            line
        }
    }.toMutableList()
    if (!replaced) {
        lines += ""
        lines += "Endpoint = $endpoint"
    }
    return lines.joinToString("\n")
}

private fun String.excludingApp(packageName: String): String {
    var inInterface = false
    var hasIncludedApplications = false
    var changed = false
    val lines = lineSequence().map { line ->
        val section = line.trim()
        if (section.startsWith("[") && section.endsWith("]")) {
            inInterface = section.equals("[Interface]", ignoreCase = true)
        }
        when {
            inInterface && section.startsWith("IncludedApplications", ignoreCase = true) -> {
                hasIncludedApplications = true
                line
            }
            inInterface && section.startsWith("ExcludedApplications", ignoreCase = true) && section.contains("=") -> {
                val apps = section.substringAfter("=")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableList()
                if (packageName !in apps) apps += packageName
                changed = true
                "ExcludedApplications = ${apps.joinToString(",")}"
            }
            else -> line
        }
    }.toMutableList()
    if (!changed && !hasIncludedApplications) {
        val interfaceIndex = lines.indexOfFirst {
            it.trim().equals("[Interface]", ignoreCase = true)
        }
        if (interfaceIndex >= 0) {
            lines.add(interfaceIndex + 1, "ExcludedApplications = $packageName")
        }
    }
    return lines.joinToString("\n")
}
