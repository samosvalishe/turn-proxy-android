package com.freeturn.app.data.server

import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.data.config.DnsMode
import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.Provider
import com.freeturn.app.data.config.ProxyMode
import com.freeturn.app.data.config.SplitTunnelMode
import com.freeturn.app.data.config.SshConfig
import com.freeturn.app.data.config.TunnelTransport
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

// Имена JSON-ключей - контракт с сохранёнными данными: менять только с миграцией.
internal object ServerJson {
    fun encodeList(list: List<Server>): String {
        val arr = JSONArray()
        list.forEach { arr.put(encode(it)) }
        return arr.toString()
    }

    /** Для чтения: неразборчивая строка показывается пустым списком. */
    fun decodeList(raw: String?): List<Server> = decodeListOrNull(raw).orEmpty()

    /**
     * null - строка не разбирается как массив. Писать поверх такого списка нельзя:
     * пустой список + новая запись молча стирали все серверы. Мусорный элемент
     * пропускается, а не роняет весь массив.
     */
    fun decodeListOrNull(raw: String?): List<Server>? {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (_: JSONException) {
            return null
        }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::decode) }
    }

    private fun encode(p: Server): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("ssh", JSONObject().apply {
            put("ip", p.ssh.ip)
            put("port", p.ssh.port)
            put("username", p.ssh.username)
            put("password", p.ssh.password)
            put("authType", p.ssh.authType)
            put("sshKey", p.ssh.sshKey)
            put("hostFingerprint", p.ssh.hostFingerprint)
            put("rootMode", p.ssh.rootMode)
            put("sudoPassword", p.ssh.sudoPassword)
        })
        put("client", JSONObject().apply {
            put("serverAddress", p.client.serverAddress)
            put("callLink", p.client.callLink)
            put("provider", p.client.provider)
            put("threads", p.client.threads)
            put("streamsPerCred", p.client.streamsPerCred)
            put("useUdp", p.client.useUdp)
            put("bond", p.client.bond)
            put("manualCaptcha", p.client.manualCaptcha)
            put("localPort", p.client.localPort)
            put("debugMode", p.client.debugMode)
            put("useCarrierDns", p.client.useCarrierDns)
            put("dnsMode", p.client.dnsMode)
            put("customDns", p.client.customDns)
            put("syncServerSwitches", p.client.syncServerSwitches)
            put("magicSwitch", p.client.magicSwitch)
            put("magicTurn", p.client.magicTurn)
            put("tunnelTransport", p.client.tunnelTransport)
            put("wireGuardConfig", p.client.wireGuardConfig)
            put("wireGuardTunnelName", p.client.wireGuardTunnelName)
            put("splitTunnelMode", p.client.splitTunnelMode)
            put("splitTunnelApps", p.client.splitTunnelApps)
            put("logsEnabled", p.client.logsEnabled)
            put("clientId", p.client.clientId)
        })
        put("proxyListen", p.proxyListen)
        put("proxyConnect", p.proxyConnect)
        put("opts", JSONObject().apply {
            put("obfProfile", p.opts.obfProfile)
            put("obfKey", p.opts.obfKey)
            put("obfTimingMs", p.opts.obfTimingMs)
            put("proxyMode", p.opts.proxyMode)
            put("kcp", encodeKcp(p.opts.kcp))
            put("method", p.opts.method)
            put("backend", p.opts.backend)
            put("wgPort", p.opts.wgPort)
            put("wgNet", p.opts.wgNet)
        })
    }

    private fun decode(o: JSONObject): Server {
        val sshO = o.optJSONObject("ssh") ?: JSONObject()
        val cliO = o.optJSONObject("client") ?: JSONObject()
        val optsO = o.optJSONObject("opts") ?: JSONObject()
        return Server(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            ssh = SshConfig(
                ip = sshO.optString("ip"),
                port = sshO.optInt("port", 22),
                username = sshO.optString("username", "root"),
                password = sshO.optString("password"),
                authType = sshO.optString("authType", SshConfig.AUTH_PASSWORD),
                sshKey = sshO.optString("sshKey"),
                hostFingerprint = sshO.optString("hostFingerprint"),
                rootMode = sshO.optString("rootMode", SshConfig.ROOT),
                sudoPassword = sshO.optString("sudoPassword")
            ),
            client = ClientConfig(
                serverAddress = cliO.optString("serverAddress"),
                callLink = cliO.optString("callLink"),
                provider = cliO.optString("provider", Provider.RELAY).let {
                    if (it in Provider.VALUES) it else Provider.RELAY
                },
                threads = cliO.optInt("threads", ClientConfig.DEFAULT_THREADS),
                streamsPerCred = cliO.optInt("streamsPerCred", ClientConfig.DEFAULT_STREAMS_PER_CRED),
                useUdp = cliO.optBoolean("useUdp", false),
                bond = cliO.optBoolean("bond", false),
                manualCaptcha = cliO.optBoolean("manualCaptcha", false),
                localPort = cliO.optString("localPort", ClientConfig.DEFAULT_LOCAL_PORT),
                debugMode = cliO.optBoolean("debugMode", false),
                useCarrierDns = cliO.optBoolean("useCarrierDns", true),
                dnsMode = cliO.optString("dnsMode", DnsMode.AUTO).let {
                    if (it in DnsMode.VALUES) it else DnsMode.AUTO
                },
                customDns = cliO.optString("customDns"),
                syncServerSwitches = cliO.optBoolean("syncServerSwitches", true),
                magicSwitch = cliO.optBoolean("magicSwitch", false),
                magicTurn = cliO.optString("magicTurn"),
                tunnelTransport = cliO.optString("tunnelTransport", TunnelTransport.NONE).let {
                    if (it in TunnelTransport.VALUES) it else TunnelTransport.NONE
                },
                wireGuardConfig = cliO.optString("wireGuardConfig"),
                wireGuardTunnelName = cliO.optString("wireGuardTunnelName").ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME },
                splitTunnelMode = cliO.optString("splitTunnelMode", SplitTunnelMode.EXCLUDE).let {
                    if (it in SplitTunnelMode.VALUES) it else SplitTunnelMode.EXCLUDE
                },
                splitTunnelApps = cliO.optString("splitTunnelApps"),
                logsEnabled = cliO.optBoolean("logsEnabled", true),
                clientId = cliO.optString("clientId")
            ),
            proxyListen = o.optString("proxyListen").ifBlank { "0.0.0.0:56000" },
            proxyConnect = o.optString("proxyConnect").ifBlank { "127.0.0.1:40537" },
            opts = ServerOpts(
                obfProfile = optsO.optString("obfProfile", ObfProfile.NONE).let {
                    if (it in ObfProfile.VALUES) it else ObfProfile.NONE
                },
                obfKey = optsO.optString("obfKey", ""),
                obfTimingMs = optsO.optInt("obfTimingMs", 0).coerceIn(0, ObfProfile.TIMING_MAX),
                proxyMode = optsO.optString("proxyMode", ProxyMode.UDP).let {
                    if (it in ProxyMode.VALUES) it else ProxyMode.UDP
                },
                kcp = decodeKcp(optsO.optJSONObject("kcp")),
                method = optsO.optString("method", ServerMethod.DOCKER).let {
                    if (it in ServerMethod.VALUES) it else ServerMethod.DOCKER
                },
                backend = optsO.optString("backend", ServerBackend.NEW).let {
                    if (it in ServerBackend.VALUES) it else ServerBackend.NEW
                },
                wgPort = optsO.optInt("wgPort", ServerBackend.DEFAULT_WG_PORT),
                wgNet = optsO.optString("wgNet").ifBlank { ServerBackend.DEFAULT_WG_NET }
            )
        )
    }

    private fun encodeKcp(p: KcpProfile): JSONObject = JSONObject().apply {
        put("noDelay", p.noDelay)
        put("interval", p.interval)
        put("resend", p.resend)
        put("nc", p.nc)
        put("sndWnd", p.sndWnd)
        put("rcvWnd", p.rcvWnd)
        put("mtu", p.mtu)
        put("ackNoDelay", p.ackNoDelay)
    }

    private fun decodeKcp(o: JSONObject?): KcpProfile {
        if (o == null) return KcpProfile.DEFAULT
        val d = KcpProfile.DEFAULT
        return KcpProfile(
            noDelay = o.optInt("noDelay", d.noDelay),
            interval = o.optInt("interval", d.interval),
            resend = o.optInt("resend", d.resend),
            nc = o.optInt("nc", d.nc),
            sndWnd = o.optInt("sndWnd", d.sndWnd),
            rcvWnd = o.optInt("rcvWnd", d.rcvWnd),
            mtu = o.optInt("mtu", d.mtu),
            ackNoDelay = o.optBoolean("ackNoDelay", d.ackNoDelay)
        )
    }
}
