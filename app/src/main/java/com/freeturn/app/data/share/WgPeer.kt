package com.freeturn.app.data.share

import com.freeturn.app.data.control.ShareListData
import java.util.Base64

/** WG-пир сервера из subcommand `share-list`. */
data class WgPeer(
    val pubkey: String,
    /** Имя из маркера ft-user. Пусто - пир без маркера (старые установки, ручные правки). */
    val name: String,
    val ip: String,
    /** Epoch-секунды последнего handshake. null - ни разу / интерфейс не поднят. */
    val lastHandshakeEpoch: Long?,
    /** На сервере сохранён клиентский conf - можно выдать ссылку повторно. */
    val hasStoredConf: Boolean,
    /** Пир самого владельца (wireguard-client.conf мастера) - не отзываем. */
    val isSelf: Boolean
)

/** Маппинг `share-list` data ([ShareListData]) в доменные [WgPeer]. */
object WgPeerParser {

    fun from(data: ShareListData): List<WgPeer> {
        val selfPub = data.selfPub
        return data.peers.mapNotNull { p ->
            val pub = p.pub.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WgPeer(
                pubkey = pub,
                name = decodeNameB64(p.nameB64),
                ip = p.ip,
                lastHandshakeEpoch = p.hs?.takeIf { it > 0 },
                hasStoredConf = p.hasConf,
                isSelf = selfPub.isNotEmpty() && pub == selfPub
            )
        }
    }
}

/** base64(UTF-8) -> имя; битое значение -> пусто. Общий хелпер парсеров share-вывода. */
internal fun decodeNameB64(b64: String?): String {
    if (b64.isNullOrBlank()) return ""
    return try {
        String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        ""
    }
}
