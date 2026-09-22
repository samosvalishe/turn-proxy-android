package com.freeturn.app.data.server

import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.ProxyMode

/**
 * Снимок серверных опций. Лежит в DataStore открытым текстом (как и SSH-секреты),
 * поэтому datastore/ исключён из системного бэкапа.
 */
data class ServerOpts(
    /** Wire-профиль обфускации: none | rtpopus | rtpopus2 | rtpopus3 (-obf-profile). */
    val obfProfile: String = ObfProfile.NONE,
    /** 64-hex obf-ключ (-obf-key). Должен совпадать на клиенте и сервере. */
    val obfKey: String = "",
    /** Межпакетная задержка мимикрии клиента (-obf-timing), мс; 0 - выкл. Требует профиля. */
    val obfTimingMs: Int = 0,
    /** Режим проброса (-mode). Сервер отвергает сессию с другим режимом. */
    val proxyMode: String = ProxyMode.UDP,
    /** ARQ tcp-режима (-kcp-*); в udp не используется. */
    val kcp: KcpProfile = KcpProfile.DEFAULT,
    val method: String = ServerMethod.DOCKER,
    val backend: String = ServerBackend.NEW,
    val wgPort: Int = ServerBackend.DEFAULT_WG_PORT,
    val wgNet: String = ServerBackend.DEFAULT_WG_NET
) {
    /** Обфускация включена, когда выбран реальный профиль. */
    val obfEnabled: Boolean get() = obfProfile != ObfProfile.NONE

    val tcpMode: Boolean get() = proxyMode == ProxyMode.TCP

    val ownWg: Boolean get() = backend == ServerBackend.NEW
}

/** Как install.sh запускает FreeTurn; WireGuard всегда на хосте. */
object ServerMethod {
    const val DOCKER = "docker"
    const val SYSTEMD = "systemd"
    val VALUES = listOf(DOCKER, SYSTEMD)
}

/** Куда сервер отдаёт трафик: свой WG ft-wg0 или чужой VPN по `-connect`. */
object ServerBackend {
    const val NEW = "new"
    const val EXTERNAL = "external"
    val VALUES = listOf(NEW, EXTERNAL)

    const val DEFAULT_WG_PORT = 51820
    const val DEFAULT_WG_NET = "10.13.13.0/24"

    /** Та же проверка, что valid_net в install.sh: сетевой адрес, префикс /16../29. */
    fun isValidNet(net: String): Boolean {
        val m = NET.matchEntire(net.trim()) ?: return false
        val octets = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return false }
        if (octets.any { it > 255 }) return false
        val prefix = m.groupValues[2].toInt()
        val ip = octets.fold(0L) { acc, o -> (acc shl 8) or o.toLong() }
        val hostMask = (1L shl (32 - prefix)) - 1
        return ip and hostMask == 0L
    }

    private val NET = Regex("""^((?:(?:0|[1-9]\d{0,2})\.){3}(?:0|[1-9]\d{0,2}))/(1[6-9]|2[0-9])$""")
}
