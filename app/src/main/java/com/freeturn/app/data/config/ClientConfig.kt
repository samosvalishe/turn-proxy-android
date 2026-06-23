package com.freeturn.app.data.config

data class ClientConfig(
    val serverAddress: String = "",
    val vkLink: String = "",
    /** Источник TURN-creds (-provider). Пока только "vk". */
    val provider: String = Provider.VK,
    val threads: Int = 12,
    /** Соответствует флагу `-streams-per-cred` ядра. Дефолт ядра = 10, наш = 6. */
    val streamsPerCred: Int = DEFAULT_STREAMS_PER_CRED,
    /** TURN-транспорт UDP (-transport udp). false = TCP/TLS (дефолт ядра и наш). */
    val useUdp: Boolean = false,
    val manualCaptcha: Boolean = false,
    /** Браузерный профиль VK-авторизации (-browser). firefox - дефолт ядра, chrome - escape-hatch. */
    val browser: String = Browser.FIREFOX,
    val localPort: String = DEFAULT_LOCAL_PORT,
    val isRawMode: Boolean = false,
    val rawCommand: String = "",
    /** Режим туннеля TCP-форвард (-mode tcp, Xray/sing-box). false = UDP-релей (WireGuard). */
    val tcpForward: Boolean = false,
    /** Bonding TCP по smux-сессиям (-bond). Только при tcpForward. Client-only в новом ядре. */
    val bond: Boolean = false,

    // Если true - добавляется флаг -debug для расширенного вывода в логах.
    val debugMode: Boolean = false,
    // Если true - в argv передаётся -dns-servers с DNS активной сети (оператор связи).
    val useCarrierDns: Boolean = true,
    // "auto" | "plain" | "doh" - соответствует флагу -dns-mode ядра.
    val dnsMode: String = DnsMode.AUTO,
    /** Ручной список DNS (-dns-servers), приоритет над [useCarrierDns]. */
    val customDns: String = "",
    /** true - изменения tcpForward/obfEnabled перезапускают удаленный сервер. */
    val syncServerSwitches: Boolean = true,
    val magicSwitch: Boolean = false,
    /** Адрес для флага -turn ядра, если magicSwitch включён. Пусто = не передавать. */
    val magicTurn: String = "",
    /** Транспорт туннеля: NONE (proxy) либо WIREGUARD (VPN). По умолчанию - без туннеля. */
    val tunnelTransport: String = TunnelTransport.NONE,
    /** Конфиг WireGuard (.conf). Пусто = WG-туннель не поднимается. */
    val wireGuardConfig: String = "",
    /** Имя WG-туннеля для GoBackend. */
    val wireGuardTunnelName: String = TunnelTransport.DEFAULT_TUNNEL_NAME,
    /** MTU WG-интерфейса. Инжектится в [Interface] при подъёме (в сыром conf не хранится). */
    val wireGuardMtu: Int = DEFAULT_WG_MTU,
    /** Режим split-tunneling: all | include | exclude. */
    val splitTunnelMode: String = SplitTunnelMode.INCLUDE,
    /** Список package-имён для include/exclude (разделители: запятая/пробел/перенос строки). */
    val splitTunnelApps: String = DEFAULT_INCLUDED_APPS,
    /** Сбор логов ядра в UI. false = ProxyServiceState.addLog глотает строки. */
    val logsEnabled: Boolean = true,
    /** -client-id для сервера (cid из share-ссылки). Пусто = общий ID устройства. */
    val clientId: String = ""
) {
    /** WG реально активен только если выбран WG-транспорт и задан непустой конфиг. */
    val wireGuardActive: Boolean
        get() = tunnelTransport == TunnelTransport.WIREGUARD && wireGuardConfig.isNotBlank()

    companion object {
        const val DEFAULT_LOCAL_PORT = "127.0.0.1:9000"
        const val DEFAULT_STREAMS_PER_CRED = 6
        const val DEFAULT_INCLUDED_APPS = "org.telegram.messenger\ncom.android.chrome\ncom.microsoft.emmx"
        // TURN-релей добавляет overhead - 1280 (минимум IPv6) против фрагментации.
        const val DEFAULT_WG_MTU = 1280
        // Диапазон валидного MTU: 1280 = минимум IPv6, 1500 = потолок Ethernet.
        const val MIN_WG_MTU = 1280
        const val MAX_WG_MTU = 1500
    }
}
