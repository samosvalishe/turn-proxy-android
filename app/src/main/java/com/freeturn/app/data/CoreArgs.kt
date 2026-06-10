package com.freeturn.app.data

/**
 * Единый источник истины для argv клиентского ядра. Использует и движок
 * ([com.freeturn.app.ProxyService] при запуске процесса), и UI (отладочная информация →
 * параметры запуска), чтобы показанная команда не расходилась с реально запускаемой.
 *
 * Возвращает аргументы БЕЗ имени бинарника. DNS оператора ([carrierDns]) резолвится
 * только в движке (зависит от активной сети); в UI передаём null — флаг -dns-servers
 * (оператор) тогда опускается, он добавится лишь при реальном запуске.
 */
object CoreArgs {

    fun client(
        cfg: ClientConfig,
        srv: ServerOpts,
        carrierDns: String? = null,
    ): List<String> = buildList {
        add("-peer"); add(cfg.serverAddress)
        add("-provider"); add(cfg.provider)
        // -link нужен только провайдеру vk (callroom URL).
        if (cfg.provider == Provider.VK) { add("-link"); add(cfg.vkLink) }
        add("-listen"); add(cfg.localPort)
        if (cfg.threads > 0) { add("-n"); add(cfg.threads.toString()) }
        // -streams-per-cred передаём только если пользователь поменял дефолт ядра (10).
        if (cfg.streamsPerCred > 0 && cfg.streamsPerCred != 10) {
            add("-streams-per-cred"); add(cfg.streamsPerCred.toString())
        }
        // tcp-форвард (Xray/sing-box) vs udp-релей (WireGuard, дефолт).
        if (cfg.tcpForward) { add("-mode"); add("tcp") }
        // Bond — client-only в новом ядре (сервер детектит по magic-префиксу); только в tcp.
        if (cfg.tcpForward && cfg.bond) add("-bond")
        if (cfg.useUdp) { add("-transport"); add("udp") }
        // Обфускация: профиль+ключ из общего serverOpts. Без валидного 64-hex не шлём —
        // ядро упадёт на DecodeKey.
        if (srv.obfEnabled && ObfProfile.isValidKey(srv.obfKey)) {
            add("-obf-profile"); add(srv.obfProfile)
            add("-obf-key"); add(srv.obfKey)
        }
        if (cfg.manualCaptcha) add("-manual-captcha")
        if (cfg.debugMode) add("-debug")
        // Ручной список DNS имеет приоритет над DNS оператора.
        val manualDns = DnsList.normalize(cfg.customDns)
        when {
            manualDns.isNotBlank() -> { add("-dns-servers"); add(manualDns) }
            cfg.useCarrierDns -> {
                carrierDns?.takeIf { it.isNotBlank() }?.let { add("-dns-servers"); add(it) }
            }
        }
        // -dns-mode: plain|doh (auto — дефолт ядра, не шлём).
        if (cfg.dnsMode == DnsMode.PLAIN || cfg.dnsMode == DnsMode.DOH) {
            add("-dns-mode"); add(cfg.dnsMode)
        }
        // Альтернативный TURN-узел вместо автоподбора (только при непустом адресе).
        if (cfg.magicSwitch) {
            cfg.magicTurn.trim().takeIf { it.isNotEmpty() }?.let { add("-turn"); add(it) }
        }
    }
}
