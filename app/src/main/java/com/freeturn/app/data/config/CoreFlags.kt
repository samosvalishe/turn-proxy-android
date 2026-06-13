package com.freeturn.app.data.config

import java.security.SecureRandom

object DnsMode {
    const val AUTO = "auto"
    const val PLAIN = "plain"
    const val DOH = "doh"
    val VALUES = listOf(AUTO, PLAIN, DOH)
}

/** Источник TURN-creds (флаг -provider ядра). Client-only. */
object Provider {
    const val VK = "vk"
    val VALUES = listOf(VK)
}

/** Wire-профиль обфускации payload (флаг -obf-profile ядра). Должен совпадать с сервером. */
object ObfProfile {
    const val NONE = "none"
    const val RTPOPUS = "rtpopus"
    val VALUES = listOf(NONE, RTPOPUS)

    private val KEY_REGEX = Regex("^[0-9a-fA-F]{64}$")

    /** Ключ -obf-key, который примет ядро (DecodeKey). Единая проверка для argv, UI и regen. */
    fun isValidKey(key: String): Boolean = key.matches(KEY_REGEX)

    /** Новый случайный obf-ключ (32 байта -> 64-hex). Ядру важен только формат. */
    fun generateKey(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}

/** Client ID (флаг -client-id ядра): авторизация по allowlist clients.json на сервере. */
object ClientId {
    private val ID_REGEX = Regex("^[0-9a-f]{32}$")

    /** Формат, который генерирует и валидирует приложение (16 байт -> 32-hex, как автоген ядра). */
    fun isValid(id: String): Boolean = id.matches(ID_REGEX)

    fun generate(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
}

/** Транспорт VPN-туннеля (пока только WireGuard) поверх локального прокси. */
object TunnelTransport {
    const val NONE = "none"
    const val WIREGUARD = "wireguard"
    const val DEFAULT_TUNNEL_NAME = "freeturn-wg"
    val VALUES = listOf(NONE, WIREGUARD)
}

/** Режим split-tunneling для WireGuard-интерфейса. */
object SplitTunnelMode {
    /** Весь трафик в туннель (только сам прокси исключён). */
    const val ALL = "all"
    /** Только перечисленные приложения идут в туннель (IncludedApplications). */
    const val INCLUDE = "include"
    /** Перечисленные приложения исключены из туннеля (ExcludedApplications). */
    const val EXCLUDE = "exclude"
    val VALUES = listOf(ALL, INCLUDE, EXCLUDE)
}
