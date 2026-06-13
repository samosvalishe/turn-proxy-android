package com.freeturn.app.data.server

import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.SshConfig
import java.util.UUID

/**
 * Именованный сервер: SSH-доступ + клиентские параметры + серверные опции.
 * Список сериализуется в DataStore (SERVERS_JSON) через [ServerJson].
 */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ssh: SshConfig = SshConfig(),
    val client: ClientConfig = ClientConfig(),
    val proxyListen: String = "0.0.0.0:56000",
    val proxyConnect: String = "127.0.0.1:40537",
    val opts: ServerOpts = ServerOpts()
) {
    companion object {
        /** Имя-заглушка для записей без имени (data-слой, ресурсы недоступны). */
        const val FALLBACK_NAME = "Без названия"
    }
}

data class ServersSnapshot(
    val list: List<Server> = emptyList(),
    val activeId: String? = null,
    /** false = initial-значение stateIn до первой эмиссии DataStore. */
    val loaded: Boolean = false
) {
    val active: Server? get() = list.firstOrNull { it.id == activeId }
}
