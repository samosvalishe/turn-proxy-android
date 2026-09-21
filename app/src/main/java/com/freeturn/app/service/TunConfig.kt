package com.freeturn.app.service

import android.content.Context
import android.net.VpnService
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.SplitTunnelRule
import com.freeturn.app.data.config.TunnelTransport
import com.freeturn.app.data.config.splitTunnelRule
import com.freeturn.app.data.isPackageInstalled
import com.freeturn.app.domain.proxy.TunnelSetup

/**
 * Переносит параметры из WG-конфига и split-tunnel в билдер tun-интерфейса.
 * Ядро получает готовый дескриптор и маршрутизацией не занимается.
 *
 * [hotspot] - раздача через SOCKS5: своё приложение обязано остаться в туннеле,
 * иначе клиенты получат канал мимо него.
 */
fun VpnService.Builder.applyTunnel(
    context: Context,
    cfg: ClientConfig,
    setup: TunnelSetup,
    hotspot: Boolean,
): VpnService.Builder = apply {
    setMtu(setup.mtu)
    setSession(cfg.wireGuardTunnelName.trim().ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME })
    // Ядро читает tun само; блокирующий режим упёрся бы в его же горутину.
    setBlocking(false)

    setup.addresses.forEach { addr ->
        val (ip, prefix) = addr.splitCidr()
        addAddress(ip, prefix)
    }
    setup.allowedIPs.forEach { route ->
        val (ip, prefix) = route.splitCidr()
        addRoute(ip, prefix)
    }
    setup.dns.forEach { addDnsServer(it) }

    val rule = splitTunnelRule(
        mode = cfg.splitTunnelMode,
        apps = cfg.splitTunnelApps,
        ownPackage = context.packageName,
        hotspot = hotspot,
        isInstalled = context::isPackageInstalled,
    )
    when (rule) {
        SplitTunnelRule.All -> Unit
        is SplitTunnelRule.Allowed -> rule.packages.forEach { addAllowedApplication(it) }
        is SplitTunnelRule.Disallowed -> rule.packages.forEach { addDisallowedApplication(it) }
    }
}

/** "10.8.0.2/32" -> ip + длина префикса; без маски - хостовый адрес. */
private fun String.splitCidr(): Pair<String, Int> {
    val ip = substringBefore('/')
    val prefix = substringAfter('/', "").toIntOrNull()
        ?: if (ip.contains(':')) 128 else 32
    return ip to prefix
}
