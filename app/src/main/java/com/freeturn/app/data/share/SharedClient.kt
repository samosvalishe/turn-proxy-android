package com.freeturn.app.data.share

import com.freeturn.app.data.control.ClientDto
import com.freeturn.app.data.control.ClientListData

/** Клиент сервера из `client-list`: запись allowlist, при своём WG - ещё и пир. */
data class SharedClient(
    /** Имя = ключ на сервере (client-conf/client-remove). */
    val name: String,
    val clientId: String,
    /** Хозяин сервера - удалять нельзя (owner_protected). */
    val isSelf: Boolean,
    /** null - пира нет (чужой VPN). */
    val wgIp: String?,
    /** Epoch-секунды последнего handshake. null - ни разу / интерфейс не поднят. */
    val lastHandshakeEpoch: Long?
) {
    companion object {
        fun from(d: ClientDto) = SharedClient(
            name = d.name,
            clientId = d.clientId,
            isSelf = d.self,
            wgIp = d.ip.takeIf { d.pub.isNotBlank() },
            lastHandshakeEpoch = d.hs.takeIf { it > 0 }
        )

        fun list(data: ClientListData): List<SharedClient> =
            data.clients.filter { it.name.isNotBlank() }.map(::from)
    }
}
