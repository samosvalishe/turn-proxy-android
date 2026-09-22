package com.freeturn.app.data.share

/**
 * Фактические параметры сервера (`client-list` share). Используется вместо локального
 * [com.freeturn.app.data.server.ServerOpts]: гостю нужны значения, с которыми сервер живёт.
 */
data class ShareInfo(
    /** Режим проброса сервера: "udp" | "tcp". */
    val mode: String = "",
    /** Пусто - серверной правды нет (ручной профиль без SSH); иначе хотя бы "none". */
    val obfProfile: String = "",
    val obfKey: String = "",
    /** Свой WG ft-wg0 -> в ссылке WG-конфиг гостя. Иначе - только FreeTurn-часть. */
    val wgBackend: Boolean = false
) {
    val hasRunArgs: Boolean get() = obfProfile.isNotEmpty()
}
