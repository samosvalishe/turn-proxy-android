package com.freeturn.app.data.config

/** Протокол выданного/импортируемого доступа - для подписей в UI. */
enum class AccessProtocol {
    PROXY, WG, AWG;

    companion object {
        fun of(wgConf: String?): AccessProtocol = when {
            wgConf.isNullOrBlank() -> PROXY
            CoreConfigJson.isAmneziaConfig(wgConf) -> AWG
            else -> WG
        }
    }
}
