package com.freeturn.app.data.share

/**
 * Фактическое состояние сервера для построения share-ссылки (subcommand
 * `share-info`: run.args на VPS). Источник правды — сервер, а не локальный
 * [com.freeturn.app.data.ServerOpts]: при выключенном syncServerSwitches
 * локальный снапшот может разойтись с реально запущенным процессом.
 */
data class ShareInfo(
    /** Режим бэкенда: "udp" | "tcp". Пусто = сервер не запускался из приложения. */
    val mode: String = "",
    val obfProfile: String = "",
    val obfKey: String = "",
    /** Есть WG-conf в /etc/wireguard → шарим VPN-доступ (peer-add). Иначе — прокси. */
    val wgBackend: Boolean = false
) {
    /** run.args найден — серверным значениям можно верить. */
    val hasRunArgs: Boolean get() = mode.isNotEmpty()
}
