package com.freeturn.app.data.config

import com.freeturn.app.data.server.ServerOpts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Схему JSON декодирует Go со `DisallowUnknownFields`: рассинхрон имён полей
 * ловится только здесь или на первом старте ядра.
 */
class CoreConfigJsonTest {

    @Test
    fun bondIsSentOnlyWhenEnabledForTcpProxy() {
        val client = ClientConfig(bond = true)
        val tcp = ServerOpts(proxyMode = ProxyMode.TCP)
        val proxy = parse(client, tcp)["proxy"]!!.jsonObject
        assertEquals("true", proxy["bond"]!!.jsonPrimitive.content)
        assertFalse(parse(client)["proxy"]!!.jsonObject.containsKey("bond"))
        assertFalse(parse(client.copy(bond = false), tcp)["proxy"]!!.jsonObject.containsKey("bond"))
        val wg = client.copy(
            tunnelTransport = TunnelTransport.WIREGUARD,
            wireGuardConfig = "[Interface]\nAddress = 10.8.0.2/32\n"
        )
        assertFalse(parse(wg, tcp)["proxy"]!!.jsonObject.containsKey("bond"))
    }

    private val base = ClientConfig(
        serverAddress = "1.2.3.4:56000",
        callLink = "https://call.example/x",
        clientId = "0123456789abcdef0123456789abcdef",
    )

    private fun parse(cfg: ClientConfig, srv: ServerOpts = ServerOpts(), carrierDns: String? = null) =
        Json.parseToJsonElement(cfg.toCoreJson(srv, carrierDns)).jsonObject

    @Test
    fun mapsFlatFields() {
        val o = parse(base)
        assertEquals("1.2.3.4:56000", o["peer"]!!.jsonPrimitive.content)
        assertEquals(Provider.RELAY, o["provider"]!!.jsonPrimitive.content)
        // Маршрутами рулит VpnService, подписок в приложении нет.
        assertEquals(false, o["routes"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("", o["subUrl"]!!.jsonPrimitive.content)
    }

    @Test
    fun ownClientIdFillsBlank() {
        val o = parse(base.copy(clientId = ""), carrierDns = null)
        assertEquals("", o["clientId"]!!.jsonPrimitive.content)

        val own = base.copy(clientId = "").toCoreJson(ServerOpts(), null, "ffffffffffffffffffffffffffffffff")
        assertTrue(own.contains("ffffffffffffffffffffffffffffffff"))
    }

    // Режим всегда awg: на чистом WG-конфиге ядро ведёт себя как в wg,
    // а wg молча срезал бы amnezia-параметры.
    @Test
    fun tunnelCarriesAwgModeForAnyConfig() {
        val plain = base.copy(
            tunnelTransport = TunnelTransport.WIREGUARD,
            wireGuardConfig = "[Interface]\nAddress = 10.8.0.2/32\n",
        )
        val tunnel = parse(plain)["tunnel"]!!.jsonObject
        assertEquals("awg", tunnel["mode"]!!.jsonPrimitive.content)
        assertEquals(ClientConfig.WG_MTU, tunnel["mtu"]!!.jsonPrimitive.content.toInt())

        val awg = plain.copy(wireGuardConfig = "[Interface]\nAddress = 10.8.0.2/32\nJc = 4\n")
        val awgTunnel = parse(awg)["tunnel"]!!.jsonObject
        assertEquals("awg", awgTunnel["mode"]!!.jsonPrimitive.content)
        assertTrue(awgTunnel["config"]!!.jsonPrimitive.content.contains("Jc = 4"))
    }

    // Набор ключей - зеркало парсера ядра (internal/tunnel/wgconf).
    @Test
    fun detectsAmneziaConfigKeys() {
        assertFalse(CoreConfigJson.isAmneziaConfig("[Interface]\nAddress = 10.8.0.2/32\nPrivateKey = xxx\n"))
        assertFalse(CoreConfigJson.isAmneziaConfig("# Jc = 4\n[Interface]\nAddress = 10.8.0.2/32\n"))
        assertFalse(CoreConfigJson.isAmneziaConfig("; S1 = 15\n[Interface]\nAddress = 10.8.0.2/32\n"))
        assertTrue(CoreConfigJson.isAmneziaConfig("[Interface]\nAddress = 10.8.0.2/32\nJc = 4\n"))
        assertTrue(CoreConfigJson.isAmneziaConfig("[Interface]\nheaderprotectionkey = 0x12345678\n"))
        assertTrue(CoreConfigJson.isAmneziaConfig("[Interface]\ns1 = 50\n"))
        assertTrue(CoreConfigJson.isAmneziaConfig("[Interface]\nH4 = 9999 ; inline comment\n"))
        assertTrue(CoreConfigJson.isAmneziaConfig("[Interface]\nJmin = 50\nJmax = 100\n"))
    }

    @Test
    fun tunnelModeNoneWhenInactive() {
        val disabled = base.copy(
            tunnelTransport = TunnelTransport.NONE,
            wireGuardConfig = "Jc = 4",
        )
        val tunnel = parse(disabled)["tunnel"]!!.jsonObject
        assertEquals("none", tunnel["mode"]!!.jsonPrimitive.content)
        assertEquals("", tunnel["config"]!!.jsonPrimitive.content)
    }

    @Test
    fun accessProtocolFollowsConf() {
        assertEquals(AccessProtocol.PROXY, AccessProtocol.of(""))
        assertEquals(AccessProtocol.PROXY, AccessProtocol.of(null))
        assertEquals(AccessProtocol.WG, AccessProtocol.of("[Interface]\nAddress = 10.8.0.2/32\n"))
        assertEquals(AccessProtocol.AWG, AccessProtocol.of("[Interface]\nJc = 4\n"))
    }

    // Лишний ключ в proxy валит старт ядра (DisallowUnknownFields), а не тест схемы.
    @Test
    fun proxyHasModeAndListen() {
        val proxy = parse(base.copy(localPort = "127.0.0.1:9001"))["proxy"]!!.jsonObject
        assertEquals(setOf("mode", "listen"), proxy.keys)
        assertEquals(ProxyMode.UDP, proxy["mode"]!!.jsonPrimitive.content)
        assertEquals("127.0.0.1:9001", proxy["listen"]!!.jsonPrimitive.content)
    }

    // В udp ядро отвергает любое отличие ARQ от своего дефолта - секции быть не должно.
    @Test
    fun kcpOnlyInTcpMode() {
        assertTrue(parse(base, ServerOpts(kcp = KcpProfile.MOBILE))["kcp"] == null)

        val tcp = parse(base, ServerOpts(proxyMode = ProxyMode.TCP, kcp = KcpProfile.MOBILE))
        assertEquals(ProxyMode.TCP, tcp["proxy"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        val kcp = tcp["kcp"]!!.jsonObject
        assertEquals(
            setOf("noDelay", "interval", "resend", "nc", "sndWnd", "rcvWnd", "mtu", "ackNoDelay"),
            kcp.keys
        )
        assertEquals(KcpProfile.MOBILE.interval, kcp["interval"]!!.jsonPrimitive.content.toInt())
        assertEquals(KcpProfile.MOBILE.ackNoDelay, kcp["ackNoDelay"]!!.jsonPrimitive.content.toBoolean())
    }

    // Встроенный туннель гонит датаграммы: tcp-режим при нём ядро бы отвергло.
    @Test
    fun tunnelForcesUdpMode() {
        val wg = base.copy(
            tunnelTransport = TunnelTransport.WIREGUARD,
            wireGuardConfig = "[Interface]\nAddress = 10.8.0.2/32\n",
        )
        val o = parse(wg, ServerOpts(proxyMode = ProxyMode.TCP))
        assertEquals(ProxyMode.UDP, o["proxy"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertTrue(o["kcp"] == null)
    }

    @Test
    fun manualDnsWinsOverCarrier() {
        val o = parse(base.copy(customDns = "1.1.1.1, 8.8.8.8"), carrierDns = "192.168.0.1")
        val servers = o["dns"]!!.jsonObject["servers"].toString()
        assertTrue(servers.contains("1.1.1.1"))
        assertTrue(servers.contains("8.8.8.8"))
        assertTrue(!servers.contains("192.168.0.1"))
    }

    @Test
    fun carrierDnsOnlyWhenSwitchOn() {
        val on = parse(base.copy(useCarrierDns = true), carrierDns = "192.168.0.1")
        assertTrue(on["dns"]!!.jsonObject["servers"].toString().contains("192.168.0.1"))

        val off = parse(base.copy(useCarrierDns = false), carrierDns = "192.168.0.1")
        assertEquals("[]", off["dns"]!!.jsonObject["servers"].toString())
    }

    @Test
    fun handoverDnsKeepsManualList() {
        val manual = base.copy(useCarrierDns = true, customDns = "1.1.1.1")
        assertEquals(listOf("1.1.1.1"), manual.coreDnsServers { error("carrier queried") })

        val carrier = base.copy(useCarrierDns = true)
        assertEquals(listOf("10.0.0.1", "10.0.0.2"), carrier.coreDnsServers { "10.0.0.1, 10.0.0.2" })
    }

    // Битый ключ уходит в ядро как есть: оно отвергнет старт с причиной, а тихий
    // none дал бы отказ сервера без объяснений.
    @Test
    fun badObfKeyReachesCore() {
        val srv = ServerOpts(obfProfile = ObfProfile.RTPOPUS, obfKey = "short")
        val obf = parse(base, srv)["obf"]!!.jsonObject
        assertEquals(ObfProfile.RTPOPUS, obf["profile"]!!.jsonPrimitive.content)
        assertEquals("short", obf["key"]!!.jsonPrimitive.content)

        val key = "a".repeat(64)
        val ok = parse(base, ServerOpts(obfProfile = ObfProfile.RTPOPUS, obfKey = key))["obf"]!!.jsonObject
        assertEquals(ObfProfile.RTPOPUS, ok["profile"]!!.jsonPrimitive.content)
        assertEquals(key, ok["key"]!!.jsonPrimitive.content)
    }

    // Пейсинг без профиля ядро отвергает - и с невалидным ключом профиль тоже гаснет.
    @Test
    fun obfTimingNeedsProfile() {
        val key = "a".repeat(64)
        val on = parse(base, ServerOpts(obfProfile = ObfProfile.RTPOPUS, obfKey = key, obfTimingMs = 20))
        assertEquals(20, on["obf"]!!.jsonObject["timingMs"]!!.jsonPrimitive.content.toInt())

        val off = parse(base, ServerOpts(obfTimingMs = 20))
        assertEquals(0, off["obf"]!!.jsonObject["timingMs"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun magicTurnOnlyWithSwitch() {
        val off = parse(base.copy(magicTurn = "turn.example:3478"))["turn"]!!.jsonObject
        assertEquals("", off["host"]!!.jsonPrimitive.content)

        val on = parse(base.copy(magicSwitch = true, magicTurn = "turn.example:3478"))["turn"]!!.jsonObject
        assertEquals("turn.example:3478", on["host"]!!.jsonPrimitive.content)
    }

    @Test
    fun nonPositiveCountsFallBackToDefaults() {
        val o = parse(base.copy(threads = 0, streamsPerCred = 0))
        assertEquals(
            ClientConfig.DEFAULT_THREADS,
            o["turn"]!!.jsonObject["n"]!!.jsonPrimitive.content.toInt()
        )
        assertEquals(
            ClientConfig.DEFAULT_STREAMS_PER_CRED,
            o["vk"]!!.jsonObject["streamsPerCred"]!!.jsonPrimitive.content.toInt()
        )
    }
}
