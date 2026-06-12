package com.freeturn.app.domain.server

/**
 * Команда, которую мы хотим выполнить на сервере. Соответствует аргументам
 * `free-turn-control.sh`. Скрипт стримится через SSH stdin (см. [ServerControl]).
 */
sealed class ServerCommand {
    data object Probe : ServerCommand()
    data object Install : ServerCommand()

    /**
     * Идемпотентная настройка WireGuard-бэкенда: бутстрап на [port] либо пир в
     * существующем conf; [endpoint] уходит в клиентский конфиг (локальный
     * free-turn-proxy клиент устройства). [adopt] — WG уже обнаружен probe-ом:
     * скрипт не бутстрапит новый, при недоступном conf отвечает ok без конфига.
     */
    data class WgSetup(
        val port: Int,
        val endpoint: String,
        val adopt: Boolean = false
    ) : ServerCommand()
    data class Start(val opts: ServerOptions) : ServerCommand()
    data object Stop : ServerCommand()
    data class FetchLogs(val lines: Int = 80) : ServerCommand()

    /** Фактические параметры запущенного сервера (run.args) — для share-ссылки. */
    data object ShareInfo : ServerCommand()

    /**
     * Новый именованный WG-пир для выдачи доступа. [nameB64] — base64(UTF-8 имени):
     * юникод/кавычки не доходят до shell. [endpoint] — локальный прокси устройства
     * получателя (рантайм подменит при подъёме туннеля). [clientId] — свежий cid
     * гостя: скрипт сажает его в allowlist (comment = имя) и хранит маппинг к пиру.
     */
    data class PeerAdd(
        val nameB64: String,
        val endpoint: String,
        val clientId: String
    ) : ServerCommand()

    /** WG-пиры + allowlist-гости одним вызовом (одна SSH-сессия на вкладку). */
    data object ShareList : ServerCommand()

    /**
     * Сохранённый клиентский conf пира — повторная выдача ссылки. [clientId] —
     * кандидат для backfill: пир без cid-маппинга (выдан до allowlist) получит его.
     */
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
            // tcp-режим (Xray/sing-box) → -mode tcp; иначе udp-релей (дефолт).
            // Bond на сервере не задаётся — ядро детектит его по magic-префиксу.
            if (opts.tcpMode) add("--mode=tcp")
            // Обфускация: -obf-profile <profile> -obf-key <key>. Профиль none → не шлём.
            if (opts.obfProfile != "none" && opts.obfKey.isNotBlank()) {
                add("--obf-profile=${opts.obfProfile}")
                add("--obf-key=${opts.obfKey}")
            }
            // cid владельца: скрипт сидит его в allowlist и включает -clients-file.
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

data class ServerOptions(
    val listen: String,
    val connect: String,
    /** true → -mode tcp (TCP-форвард). false → udp-релей (дефолт ядра). */
    val tcpMode: Boolean = false,
    /** Wire-профиль обфускации: none | rtpopus. */
    val obfProfile: String = "none",
    /** 64-hex obf-ключ. Пустая строка → запуск без обфускации. */
    val obfKey: String = "",
    /**
     * Client ID владельца (32 hex). Непустой → скрипт сажает его в clients.json
     * и запускает сервер с -clients-file (авторизация по allowlist).
     */
    val clientId: String = ""
)
