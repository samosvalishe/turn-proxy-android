package com.freeturn.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID


/**
 * Именованный снимок настроек: SSH-сервер + клиентские параметры.
 * Хранится сериализованным в DataStore. Активный профиль — это id из общего
 * списка; его SshConfig/ClientConfig дублируются в legacy-ключах prefs, чтобы
 * существующие экраны и ViewModel могли работать без переписывания.
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ssh: SshConfig = SshConfig(),
    val client: ClientConfig = ClientConfig(),
    val proxyListen: String = "0.0.0.0:56000",
    val proxyConnect: String = "127.0.0.1:40537",
    val server: AppPreferences.ServerOpts = AppPreferences.ServerOpts()
)

data class ProfilesSnapshot(
    val list: List<Profile> = emptyList(),
    val activeId: String? = null,
    /** false = initial-значение stateIn до первой эмиссии DataStore. */
    val loaded: Boolean = false
) {
    val active: Profile? get() = list.firstOrNull { it.id == activeId }
}

internal object ProfileJson {
    fun encodeList(list: List<Profile>): String {
        val arr = JSONArray()
        list.forEach { arr.put(encode(it)) }
        return arr.toString()
    }

    fun decodeList(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { decode(arr.getJSONObject(it)) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun encode(p: Profile): JSONObject = JSONObject().apply {
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
        })
        put("client", JSONObject().apply {
            put("serverAddress", p.client.serverAddress)
            put("vkLink", p.client.vkLink)
            put("provider", p.client.provider)
            put("threads", p.client.threads)
            put("streamsPerCred", p.client.streamsPerCred)
            put("useUdp", p.client.useUdp)
            put("manualCaptcha", p.client.manualCaptcha)
            put("localPort", p.client.localPort)
            put("isRawMode", p.client.isRawMode)
            put("rawCommand", p.client.rawCommand)
            put("tcpForward", p.client.tcpForward)
            put("bond", p.client.bond)

            put("debugMode", p.client.debugMode)
            put("useCarrierDns", p.client.useCarrierDns)
            put("dnsMode", p.client.dnsMode)
            put("syncServerSwitches", p.client.syncServerSwitches)
            put("magicSwitch", p.client.magicSwitch)
            put("magicTurn", p.client.magicTurn)
            put("tunnelTransport", p.client.tunnelTransport)
            put("wireGuardConfig", p.client.wireGuardConfig)
            put("wireGuardTunnelName", p.client.wireGuardTunnelName)
            put("splitTunnelMode", p.client.splitTunnelMode)
            put("splitTunnelApps", p.client.splitTunnelApps)
            put("logsEnabled", p.client.logsEnabled)
        })
        put("proxyListen", p.proxyListen)
        put("proxyConnect", p.proxyConnect)
        put("server", JSONObject().apply {
            put("obfProfile", p.server.obfProfile)
            put("obfKey", p.server.obfKey)
        })
    }

    private fun decode(o: JSONObject): Profile {
        val sshO = o.optJSONObject("ssh") ?: JSONObject()
        val cliO = o.optJSONObject("client") ?: JSONObject()
        val srvO = o.optJSONObject("server") ?: JSONObject()
        return Profile(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name").ifBlank { "Без названия" },
            ssh = SshConfig(
                ip = sshO.optString("ip"),
                port = sshO.optInt("port", 22),
                username = sshO.optString("username", "root"),
                password = sshO.optString("password"),
                authType = sshO.optString("authType", "PASSWORD"),
                sshKey = sshO.optString("sshKey"),
                hostFingerprint = sshO.optString("hostFingerprint")
            ),
            client = ClientConfig(
                serverAddress = cliO.optString("serverAddress"),
                vkLink = cliO.optString("vkLink"),
                provider = cliO.optString("provider", Provider.VK).let {
                    if (it in Provider.ALL) it else Provider.VK
                },
                threads = cliO.optInt("threads", 4),
                streamsPerCred = cliO.optInt("streamsPerCred", 10),
                useUdp = cliO.optBoolean("useUdp", true),
                manualCaptcha = cliO.optBoolean("manualCaptcha", false),
                localPort = cliO.optString("localPort", "127.0.0.1:9000"),
                isRawMode = cliO.optBoolean("isRawMode", false),
                rawCommand = cliO.optString("rawCommand"),
                tcpForward = cliO.optBoolean("tcpForward", false),
                bond = cliO.optBoolean("bond", false),

                debugMode = cliO.optBoolean("debugMode", false),
                useCarrierDns = cliO.optBoolean("useCarrierDns", false),
                dnsMode = cliO.optString("dnsMode", DnsMode.AUTO).let {
                    if (it in DnsMode.ALL) it else DnsMode.AUTO
                },
                syncServerSwitches = cliO.optBoolean("syncServerSwitches", true),
                magicSwitch = cliO.optBoolean("magicSwitch", false),
                magicTurn = cliO.optString("magicTurn"),
                tunnelTransport = cliO.optString("tunnelTransport", TunnelTransport.WIREGUARD).let {
                    if (it in TunnelTransport.ALL) it else TunnelTransport.WIREGUARD
                },
                wireGuardConfig = cliO.optString("wireGuardConfig"),
                wireGuardTunnelName = cliO.optString("wireGuardTunnelName").ifBlank { TunnelTransport.DEFAULT_TUNNEL_NAME },
                splitTunnelMode = cliO.optString("splitTunnelMode", SplitTunnelMode.ALL).let {
                    if (it in SplitTunnelMode.VALUES) it else SplitTunnelMode.ALL
                },
                splitTunnelApps = cliO.optString("splitTunnelApps"),
                logsEnabled = cliO.optBoolean("logsEnabled", true)
            ),
            proxyListen = o.optString("proxyListen").ifBlank { "0.0.0.0:56000" },
            proxyConnect = o.optString("proxyConnect").ifBlank { "127.0.0.1:40537" },
            server = AppPreferences.ServerOpts(
                obfProfile = srvO.optString("obfProfile", ObfProfile.NONE).let {
                    if (it in ObfProfile.ALL) it else ObfProfile.NONE
                },
                obfKey = srvO.optString("obfKey", "")
            )
        )
    }
}
