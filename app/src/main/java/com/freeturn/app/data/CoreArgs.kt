package com.freeturn.app.data

import com.freeturn.app.data.config.Browser
import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.DnsMode
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.Provider
import com.freeturn.app.data.server.ServerOpts

/**
 * Единый источник истины для argv клиентского ядра.
 * Возвращает аргументы без имени бинарника. [carrierDns] и [ownClientId] резолвятся движком.
 */
object
CoreArgs {

    fun client(
        cfg: ClientConfig,
        srv: ServerOpts,
        carrierDns: String? = null,
        ownClientId: String? = null,
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
        // Bond - client-only в новом ядре (сервер детектит по magic-префиксу); только в tcp.
        if (cfg.tcpForward && cfg.bond) add("-bond")
        if (cfg.useUdp) { add("-transport"); add("udp") }
        // Обфускация: шлём только валидный 64-hex ключ, иначе ядро упадет.
        if (srv.obfEnabled && ObfProfile.isValidKey(srv.obfKey)) {
            add("-obf-profile"); add(srv.obfProfile)
            add("-obf-key"); add(srv.obfKey)
        }
        if (cfg.manualCaptcha) add("-manual-captcha")
        // -browser: профиль VK-auth, только vk. firefox - дефолт ядра (не шлём).
        val browser = cfg.browser.takeIf { it in Browser.VALUES } ?: Browser.DEFAULT
        if (cfg.provider == Provider.VK && browser != Browser.FIREFOX) { add("-browser"); add(browser) }
        if (cfg.debugMode) add("-debug")
        // Ручной список DNS имеет приоритет над DNS оператора.
        val manualDns = DnsList.normalize(cfg.customDns)
        when {
            manualDns.isNotBlank() -> { add("-dns-servers"); add(manualDns) }
            cfg.useCarrierDns -> {
                carrierDns?.takeIf { it.isNotBlank() }?.let { add("-dns-servers"); add(it) }
            }
        }
        // -dns-mode: plain|doh (auto - дефолт ядра, не шлём).
        if (cfg.dnsMode == DnsMode.PLAIN || cfg.dnsMode == DnsMode.DOH) {
            add("-dns-mode"); add(cfg.dnsMode)
        }
        // Альтернативный TURN-узел вместо автоподбора (только при непустом адресе).
        if (cfg.magicSwitch) {
            cfg.magicTurn.trim().takeIf { it.isNotEmpty() }?.let { add("-turn"); add(it) }
        }
        // -client-id: cid из ссылки или общий ID устройства.
        val clientId = cfg.clientId.ifBlank { ownClientId.orEmpty() }
        if (clientId.isNotBlank()) { add("-client-id"); add(clientId) }
    }

    // Секреты: лог виден на экране и шарится пользователем.
    private val SENSITIVE_FLAGS = setOf("-obf-key", "-link", "-client-id")

    fun redactForLog(args: List<String>): String = buildString {
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (isNotEmpty()) append(' ')
            append(arg)
            if (arg in SENSITIVE_FLAGS && i + 1 < args.size) {
                append(' ').append("••••••")
                i += 2
            } else {
                i++
            }
        }
    }
}
