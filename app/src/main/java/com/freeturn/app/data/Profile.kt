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
    val proxyConnect: String = "127.0.0.1:40537"
)

data class ProfilesSnapshot(
    val list: List<Profile> = emptyList(),
    val activeId: String? = null
) {
    val active: Profile? get() = list.firstOrNull { it.id == activeId }
}

private fun decodeProfileVkLinks(o: JSONObject): List<String> {
    val arr = o.optJSONArray("vkLinks")
    if (arr != null) {
        val out = (0 until arr.length()).mapNotNull {
            arr.optString(it).trim().takeIf { s -> s.isNotEmpty() }
        }
        if (out.isNotEmpty()) return out
    }
    val legacy = o.optString("vkLink").trim()
    return if (legacy.isNotEmpty()) listOf(legacy) else emptyList()
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
            // Legacy single-link для обратной совместимости со старыми билдами.
            put("vkLink", p.client.vkLinks.firstOrNull().orEmpty())
            put("vkLinks", JSONArray().apply { p.client.vkLinks.forEach { put(it) } })
            put("threads", p.client.threads)
            put("allocsPerStream", p.client.allocsPerStream)
            put("useUdp", p.client.useUdp)
            put("manualCaptcha", p.client.manualCaptcha)
            put("localPort", p.client.localPort)
            put("isRawMode", p.client.isRawMode)
            put("rawCommand", p.client.rawCommand)
            put("vlessMode", p.client.vlessMode)
            put("dnsMode", p.client.dnsMode)
            put("forcePort443", p.client.forcePort443)
            put("debugMode", p.client.debugMode)
        })
        put("proxyListen", p.proxyListen)
        put("proxyConnect", p.proxyConnect)
    }

    private fun decode(o: JSONObject): Profile {
        val sshO = o.optJSONObject("ssh") ?: JSONObject()
        val cliO = o.optJSONObject("client") ?: JSONObject()
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
                vkLinks = decodeProfileVkLinks(cliO),
                threads = cliO.optInt("threads", 4),
                allocsPerStream = cliO.optInt("allocsPerStream", 1),
                useUdp = cliO.optBoolean("useUdp", true),
                manualCaptcha = cliO.optBoolean("manualCaptcha", false),
                localPort = cliO.optString("localPort", "127.0.0.1:9000"),
                isRawMode = cliO.optBoolean("isRawMode", false),
                rawCommand = cliO.optString("rawCommand"),
                vlessMode = cliO.optBoolean("vlessMode", false),
                dnsMode = cliO.optString("dnsMode", DnsMode.AUTO).let {
                    if (it in DnsMode.ALL) it else DnsMode.AUTO
                },
                forcePort443 = cliO.optBoolean("forcePort443", false),
                debugMode = cliO.optBoolean("debugMode", false)
            ),
            proxyListen = o.optString("proxyListen").ifBlank { "0.0.0.0:56000" },
            proxyConnect = o.optString("proxyConnect").ifBlank { "127.0.0.1:40537" }
        )
    }
}
