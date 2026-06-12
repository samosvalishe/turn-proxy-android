package com.freeturn.app.data.share

import com.freeturn.app.data.ObfProfile
import com.freeturn.app.data.Server

/**
 * Собирает freeturn://-ссылку для нового пользователя. mode/obf берутся из
 * [ShareInfo] (фактический run.args сервера); локальные Server.opts — фоллбэк
 * для серверов, ни разу не запускавшихся из приложения. vkLink в ссылку не
 * входит: звонок уникален на каждого клиента, получатель вводит свой.
 */
object ShareLinkBuilder {

    fun build(
        server: Server,
        info: ShareInfo,
        userName: String,
        wgConf: String?,
        clientId: String = ""
    ): String {
        val tcpMode = if (info.hasRunArgs) info.mode == "tcp" else server.client.tcpForward
        val obfProfile = if (info.hasRunArgs) info.obfProfile else server.opts.obfProfile
        val obfKey = if (info.hasRunArgs) info.obfKey else server.opts.obfKey
        return FreeturnLink(
            provider = server.client.provider,
            peer = server.client.serverAddress,
            transport = if (server.client.useUdp) "udp" else "",
            mode = if (tcpMode) "tcp" else "",
            bond = tcpMode && server.client.bond,
            obfProfile = if (ObfProfile.isValidKey(obfKey)) obfProfile else "",
            obfKey = if (ObfProfile.isValidKey(obfKey)) obfKey else "",
            clientId = clientId.trim(),
            name = userName.trim(),
            wgConf = wgConf?.let(::normalizeConf).orEmpty()
        ).encode()
    }

    /** Срезает комментарии и пустые строки WG-conf — короче ссылка, плотнее QR. */
    internal fun normalizeConf(conf: String): String =
        conf.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
            .joinToString("\n")
}
