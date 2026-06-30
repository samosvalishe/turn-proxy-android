package com.freeturn.app.domain.server

/** Команда для сервера (аргументы `free-turn-control.sh`, см. [ServerControl]). */
sealed class ServerCommand {
    data object Probe : ServerCommand()
    data object Install : ServerCommand()

    /** Идемпотентная настройка WG-бэкенда (бутстрап или создание пира). */
    data class WgSetup(
        val port: Int,
        val endpoint: String,
        val adopt: Boolean = false
    ) : ServerCommand()
    data class Start(val opts: ServerStartOptions) : ServerCommand()
    data object Stop : ServerCommand()
    data class FetchLogs(val lines: Int = 80) : ServerCommand()

    /** Фактические параметры запущенного сервера (run.args) - для share-ссылки. */
    data object ShareInfo : ServerCommand()

    /** Новый WG-пир для доступа. [clientId] добавляется в allowlist. */
    data class PeerAdd(
        val nameB64: String,
        val endpoint: String,
        val clientId: String
    ) : ServerCommand()

    /** WG-пиры + allowlist-гости одним вызовом (одна SSH-сессия на вкладку). */
    data object ShareList : ServerCommand()

    /** Сохранённый conf пира (повторная выдача ссылки). */
    data class PeerConf(
        val pubkey: String,
        val clientId: String,
        val nameB64: String
    ) : ServerCommand()
    data class PeerRemove(val pubkey: String) : ServerCommand()

    /** Гость без WG-пира (tcp/Xray-бэкенд): только Client ID в allowlist. */
    data class ClientAdd(val nameB64: String, val clientId: String) : ServerCommand()
    data class ClientRemove(val clientId: String) : ServerCommand()

    fun toArgv(): List<String> = when (this) {
        is Probe -> listOf("probe")
        is Install -> listOf("install")
        is WgSetup -> buildList {
            add("wg-setup")
            add("--port=$port")
            add("--endpoint=$endpoint")
            if (adopt) add("--adopt=1")
        }
        is Start -> buildList {
            add("start")
            add("--listen=${opts.listen}")
            add("--connect=${opts.connect}")
            // Режим работы (tcp/udp). Bond детектится сервером автоматически.
            if (opts.tcpMode) add("--mode=tcp")
            // Обфускация (profile != none).
            if (opts.obfProfile != "none" && opts.obfKey.isNotBlank()) {
                add("--obf-profile=${opts.obfProfile}")
                add("--obf-key=${opts.obfKey}")
            }
            // cid владельца для allowlist.
            if (opts.clientId.isNotBlank()) add("--client-id=${opts.clientId}")
        }
        is Stop -> listOf("stop")
        is FetchLogs -> listOf("logs", "--tail=$lines")
        is ShareInfo -> listOf("share-info")
        is PeerAdd -> buildList {
            add("peer-add")
            add("--name-b64=$nameB64")
            add("--endpoint=$endpoint")
            if (clientId.isNotBlank()) add("--client-id=$clientId")
        }
        is ShareList -> listOf("share-list")
        is PeerConf -> buildList {
            add("peer-conf")
            add("--pubkey=$pubkey")
            if (clientId.isNotBlank()) add("--client-id=$clientId")
            if (nameB64.isNotBlank()) add("--name-b64=$nameB64")
        }
        is PeerRemove -> listOf("peer-remove", "--pubkey=$pubkey")
        is ClientAdd -> listOf("client-add", "--name-b64=$nameB64", "--client-id=$clientId")
        is ClientRemove -> listOf("client-remove", "--client-id=$clientId")
    }
}

data class ServerStartOptions(
    val listen: String,
    val connect: String,
    /** true -> -mode tcp (TCP-форвард). false -> udp-релей (дефолт ядра). */
    val tcpMode: Boolean = false,
    /** Wire-профиль обфускации: none | rtpopus | rtpopus2 | rtpopus3. */
    val obfProfile: String = "none",
    /** 64-hex obf-ключ. Пустая строка -> запуск без обфускации. */
    val obfKey: String = "",
    /** Client ID владельца (добавляется в clients.json). */
    val clientId: String = ""
)
