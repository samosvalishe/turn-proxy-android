package com.freeturn.app.data

/** Снимок серверных obf-опций. obfKey хранится в шифрованном хранилище AppPreferences. */
data class ServerOpts(
    /** Wire-профиль обфускации: none | rtpopus (-obf-profile). */
    val obfProfile: String = ObfProfile.NONE,
    /** 64-hex obf-ключ (-obf-key). Должен совпадать на клиенте и сервере. */
    val obfKey: String = ""
) {
    /** Обфускация включена, когда выбран реальный профиль. */
    val obfEnabled: Boolean get() = obfProfile != ObfProfile.NONE
}
