package com.freeturn.app.domain.server

/**
 * Команды управления сервером, которые транслируются в подкоманды
 * `vk-turn-control.sh`. Скрипт стримится через SSH stdin (см. [ServerControl]).
 */
sealed class ServerCommand {
    data object Probe : ServerCommand()
    data object Install : ServerCommand()
    data class Start(val opts: ServerOptions) : ServerCommand()
    data object Stop : ServerCommand()
    data object GenWrapKey : ServerCommand()
    data class FetchLogs(val lines: Int = 80) : ServerCommand()

    fun toArgv(): List<String> = when (this) {
        is Probe -> listOf("probe")
        is Install -> listOf("install")
        is Start -> buildList {
            add("start")
            add("--listen=${opts.listen}")
            add("--connect=${opts.connect}")
            if (opts.vless) add("--vless")
            if (opts.vlessBond) add("--vless-bond")
            if (opts.wrapKey.isNotBlank()) add("--wrap-key=${opts.wrapKey}")
        }
        is Stop -> listOf("stop")
        is GenWrapKey -> listOf("gen-wrap-key")
        is FetchLogs -> listOf("logs", "--tail=$lines")
    }
}

data class ServerOptions(
    val listen: String,
    val connect: String,
    val vless: Boolean = false,
    val vlessBond: Boolean = false,
    /** 64-hex wrap-ключ. Пустая строка → передача без -wrap. */
    val wrapKey: String = ""
)
