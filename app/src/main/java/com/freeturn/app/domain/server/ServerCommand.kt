package com.freeturn.app.domain.server

import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.ProxyMode
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerBackend

/** RPC-команды install.sh (proto 3); argv - `<команда> --ключ=значение`. */
sealed class ServerCommand {
    data object Probe : ServerCommand()
    /**
     * @param update переустановить сервер, даже если версия не менялась.
     * @param bin путь на сервере к загруженной локальной сборке - ставится вместо релиза.
     */
    data class Apply(
        val opts: ApplyOptions,
        val update: Boolean = false,
        val bin: String = ""
    ) : ServerCommand()
    data object Stop : ServerCommand()
    data class Logs(val tail: Int = 80) : ServerCommand()
    data object ClientList : ServerCommand()
    data class ClientAdd(val name: String) : ServerCommand()
    data class ClientConf(val name: String) : ServerCommand()
    data class ClientRemove(val name: String) : ServerCommand()
    data object Uninstall : ServerCommand()

    fun toArgv(): List<String> = when (this) {
        is Probe -> listOf("probe")
        is Apply -> buildList {
            addAll(opts.toArgv())
            if (update) add("--update")
            if (bin.isNotEmpty()) add("--bin=$bin")
        }
        is Stop -> listOf("stop")
        is Logs -> listOf("logs", "--tail=$tail")
        is ClientList -> listOf("client-list")
        is ClientAdd -> listOf("client-add", "--name=$name")
        is ClientConf -> listOf("client-conf", "--name=$name")
        is ClientRemove -> listOf("client-remove", "--name=$name")
        // Снос целиком: приложение не ведёт серверы, где FreeTurn удалён, а WG оставлен.
        is Uninstall -> listOf("uninstall", "--target=all", "--purge")
    }
}

/**
 * Полный конфиг сервера для `apply`: скрипт хранит его в install.conf, поэтому шлём
 * всё, а не дельту - иначе на сервере осталось бы значение прошлой установки.
 */
data class ApplyOptions(
    val method: String,
    val backend: String,
    val listenPort: Int,
    /** host:port чужого VPN; только для [ServerBackend.EXTERNAL]. */
    val connect: String = "",
    val wgPort: Int = ServerBackend.DEFAULT_WG_PORT,
    val wgNet: String = ServerBackend.DEFAULT_WG_NET,
    val proxyMode: String = ProxyMode.UDP,
    val kcp: KcpProfile = KcpProfile.DEFAULT,
    val obfProfile: String = ObfProfile.NONE,
    /** Пусто при включённой обфускации - ключ сгенерирует сервер и вернёт в ответе. */
    val obfKey: String = ""
) {
    fun toArgv(): List<String> = buildList {
        add("apply")
        add("--method=$method")
        add("--backend=$backend")
        add("--listen-port=$listenPort")
        if (backend == ServerBackend.EXTERNAL) {
            add("--connect=$connect")
        } else {
            add("--wg-port=$wgPort")
            add("--wg-net=$wgNet")
        }
        add("--mode=$proxyMode")
        add("--obf-profile=$obfProfile")
        if (obfProfile != ObfProfile.NONE && obfKey.isNotBlank()) add("--obf-key=$obfKey")
        if (proxyMode == ProxyMode.TCP) addAll(kcpArgs(kcp))
    }
}

/** KCP_ARGS на сервере перезаписываются целиком: шлём весь профиль, не только отличия. */
private fun kcpArgs(p: KcpProfile): List<String> = listOf(
    "--kcp-nodelay=${p.noDelay}",
    "--kcp-interval=${p.interval}",
    "--kcp-resend=${p.resend}",
    "--kcp-nc=${p.nc}",
    "--kcp-sndwnd=${p.sndWnd}",
    "--kcp-rcvwnd=${p.rcvWnd}",
    "--kcp-mtu=${p.mtu}",
    "--kcp-acknodelay=${p.ackNoDelay}"
)

fun Server.applyOptions(): ApplyOptions = ApplyOptions(
    method = opts.method,
    backend = opts.backend,
    listenPort = proxyListen.substringAfterLast(':').toIntOrNull() ?: DEFAULT_LISTEN_PORT,
    connect = proxyConnect,
    wgPort = opts.wgPort,
    wgNet = opts.wgNet,
    proxyMode = opts.proxyMode,
    kcp = opts.kcp,
    obfProfile = opts.obfProfile,
    obfKey = opts.obfKey
)

private const val DEFAULT_LISTEN_PORT = 56000

/** Итог `apply`: что сервер выдал сам и что надо положить в профиль. */
data class ApplyResult(
    val ownerClientId: String,
    /** Клиентский WG-конфиг хозяина; пусто при чужом VPN. */
    val ownerConf: String,
    val obfKey: String
)

/** Client ID хозяина и ключ после apply - иначе клиент не пройдёт allowlist или OBF. */
fun Server.withApplied(r: ApplyResult): Server = copy(
    client = client.copy(clientId = r.ownerClientId.ifBlank { client.clientId }),
    opts = opts.copy(obfKey = r.obfKey)
)
