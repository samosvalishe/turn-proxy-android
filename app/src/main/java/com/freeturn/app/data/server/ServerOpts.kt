package com.freeturn.app.data.server

import com.freeturn.app.data.config.ObfProfile

/** Снимок серверных obf-опций. obfKey хранится в шифрованном хранилище AppPreferences. */
data class ServerOpts(
    /** Wire-профиль обфускации: none | rtpopus | rtpopus2 | rtpopus3 (-obf-profile). */
    val obfProfile: String = ObfProfile.NONE,
    /** 64-hex obf-ключ (-obf-key). Должен совпадать на клиенте и сервере. */
    val obfKey: String = ""
) {
    /** Обфускация включена, когда выбран реальный профиль. */
    val obfEnabled: Boolean get() = obfProfile != ObfProfile.NONE
}
