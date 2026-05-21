package com.freeturn.app.domain

import android.net.Uri
import android.util.Base64
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.TunnelRoute
import com.freeturn.app.data.TunnelTransport
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

data class ImportedXrayProfile(
    val name: String,
    val client: ClientConfig
)

object XrayProfileImporter {
    fun import(raw: String): ImportedXrayProfile {
        val value = raw.trim()
        require(value.isNotBlank()) { "профиль пуст" }
        val (name, config) = when {
            value.startsWith("{") -> {
                val json = JSONObject(value)
                val name = json.optString("remarks")
                    .ifBlank { json.optString("name") }
                    .ifBlank { "Xray profile" }
                name to json
            }
            value.startsWith("vless://", ignoreCase = true) -> parseVless(value)
            value.startsWith("vmess://", ignoreCase = true) -> parseVmess(value)
            value.startsWith("trojan://", ignoreCase = true) -> parseTrojan(value)
            value.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(value)
            value.startsWith("hysteria2://", ignoreCase = true) ||
                value.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(value)
            else -> throw IllegalArgumentException("неподдерживаемый формат профиля")
        }
        return ImportedXrayProfile(
            name = name.ifBlank { "Xray profile" },
            client = ClientConfig(
                xrayEnabled = true,
                xrayConfig = config.toString(2),
                xrayProfileName = name.ifBlank { "Xray profile" },
                tunnelTransport = TunnelTransport.VLESS,
                tunnelRoute = TunnelRoute.DIRECT_XRAY
            )
        )
    }

    private fun parseVless(raw: String): Pair<String, JSONObject> {
        val uri = Uri.parse(raw)
        val name = uri.fragment?.urlDecode()?.ifBlank { null } ?: uri.host.orEmpty()
        val user = uri.userInfo.orEmpty()
        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", uri.host)
                    .put("port", uri.port.takeIf { it > 0 } ?: 443)
                    .put("users", JSONArray().put(JSONObject()
                        .put("id", user)
                        .put("encryption", uri.getQueryParameter("encryption") ?: "none")
                        .putIfPresent("flow", uri.getQueryParameter("flow"))
                    ))
            )))
            .putStreamSettings(uri)
        return name to baseConfig(outbound)
    }

    private fun parseVmess(raw: String): Pair<String, JSONObject> {
        val payload = raw.substringAfter("vmess://")
        val json = JSONObject(payload.base64Decode())
        val name = json.optString("ps").ifBlank { json.optString("add") }
        val outbound = JSONObject()
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", json.optString("add"))
                    .put("port", json.optString("port").toIntOrNull() ?: json.optInt("port", 443))
                    .put("users", JSONArray().put(JSONObject()
                        .put("id", json.optString("id"))
                        .put("alterId", json.optString("aid").toIntOrNull() ?: json.optInt("aid", 0))
                        .put("security", json.optString("scy").ifBlank { "auto" })
                    ))
            )))
        val stream = JSONObject()
            .put("network", json.optString("net").ifBlank { "tcp" })
            .put("security", json.optString("tls").ifBlank { "none" })
        outbound.put("streamSettings", stream)
        return name to baseConfig(outbound)
    }

    private fun parseTrojan(raw: String): Pair<String, JSONObject> {
        val uri = Uri.parse(raw)
        val name = uri.fragment?.urlDecode()?.ifBlank { null } ?: uri.host.orEmpty()
        val outbound = JSONObject()
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", uri.host)
                    .put("port", uri.port.takeIf { it > 0 } ?: 443)
                    .put("password", uri.userInfo.orEmpty())
            )))
            .putStreamSettings(uri)
        return name to baseConfig(outbound)
    }

    private fun parseShadowsocks(raw: String): Pair<String, JSONObject> {
        val uri = Uri.parse(raw)
        val name = uri.fragment?.urlDecode()?.ifBlank { null } ?: uri.host.orEmpty()
        val decodedFull = raw.substringAfter("ss://").substringBefore("#").substringBefore("?")
            .takeIf { !it.contains("@") }
            ?.base64DecodeOrNull()
        val userInfo = uri.userInfo?.takeIf { it.contains(":") }
            ?: decodedFull?.substringBefore("@")
            ?: raw.substringAfter("ss://").substringBefore("#").substringBefore("?")
                .substringBefore("@").base64Decode()
        val method = userInfo.substringBefore(":")
        val password = userInfo.substringAfter(":", "")
        val decodedHostPort = decodedFull?.substringAfter("@", "")
        val host = decodedHostPort?.substringBeforeLast(":") ?: uri.host
        val port = decodedHostPort?.substringAfterLast(":", "")?.toIntOrNull()
            ?: uri.port.takeIf { it > 0 }
            ?: 8388
        val outbound = JSONObject()
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("method", method)
                    .put("password", password)
            )))
        return name to baseConfig(outbound)
    }

    private fun parseHysteria2(raw: String): Pair<String, JSONObject> {
        val uri = Uri.parse(raw)
        val name = uri.fragment?.urlDecode()?.ifBlank { null } ?: uri.host.orEmpty()
        val outbound = JSONObject()
            .put("protocol", "hysteria2")
            .put("settings", JSONObject()
                .put("address", uri.host)
                .put("port", uri.port.takeIf { it > 0 } ?: 443)
                .put("password", uri.userInfo.orEmpty())
            )
            .putStreamSettings(uri)
        return name to baseConfig(outbound)
    }

    private fun baseConfig(outbound: JSONObject): JSONObject = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("inbounds", JSONArray().put(
            JSONObject()
                .put("listen", "127.0.0.1")
                .put("port", 10808)
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        ))
        .put("outbounds", JSONArray().put(outbound))
}

private fun JSONObject.putIfPresent(key: String, value: String?): JSONObject {
    if (!value.isNullOrBlank()) put(key, value)
    return this
}

private fun JSONObject.putStreamSettings(uri: Uri): JSONObject {
    val network = uri.getQueryParameter("type")
        ?: uri.getQueryParameter("net")
        ?: uri.getQueryParameter("network")
    val security = uri.getQueryParameter("security")
        ?: uri.getQueryParameter("tls")
    if (network.isNullOrBlank() && security.isNullOrBlank()) return this
    val stream = JSONObject()
    if (!network.isNullOrBlank()) stream.put("network", network)
    if (!security.isNullOrBlank()) stream.put("security", security)
    uri.getQueryParameter("sni")
        ?.takeIf { it.isNotBlank() }
        ?.let { stream.put("tlsSettings", JSONObject().put("serverName", it)) }
    uri.getQueryParameter("alpn")
        ?.takeIf { it.isNotBlank() }
        ?.let { value ->
            val alpn = JSONArray()
            value.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { alpn.put(it) }
            val tls = stream.optJSONObject("tlsSettings") ?: JSONObject()
            tls.put("alpn", alpn)
            stream.put("tlsSettings", tls)
        }
    if (security.equals("reality", ignoreCase = true)) {
        val reality = JSONObject()
        uri.getQueryParameter("sni")?.takeIf { it.isNotBlank() }?.let { reality.put("serverName", it) }
        uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() }?.let { reality.put("fingerprint", it) }
        uri.getQueryParameter("pbk")?.takeIf { it.isNotBlank() }?.let { reality.put("publicKey", it) }
        uri.getQueryParameter("sid")?.takeIf { it.isNotBlank() }?.let { reality.put("shortId", it) }
        uri.getQueryParameter("spx")?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
        if (reality.length() > 0) stream.put("realitySettings", reality)
    }
    when (network?.lowercase()) {
        "ws" -> {
            val ws = JSONObject()
            uri.getQueryParameter("path")?.takeIf { it.isNotBlank() }?.let { ws.put("path", it) }
            uri.getQueryParameter("host")?.takeIf { it.isNotBlank() }?.let {
                ws.put("headers", JSONObject().put("Host", it))
            }
            if (ws.length() > 0) stream.put("wsSettings", ws)
        }
        "grpc" -> {
            val serviceName = uri.getQueryParameter("serviceName")
                ?: uri.getQueryParameter("service")
            if (!serviceName.isNullOrBlank()) {
                stream.put("grpcSettings", JSONObject().put("serviceName", serviceName))
            }
        }
        "tcp" -> {
            uri.getQueryParameter("headerType")
                ?.takeIf { it.isNotBlank() && it != "none" }
                ?.let { stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", it))) }
        }
    }
    put("streamSettings", stream)
    return this
}

private fun String.urlDecode(): String =
    URLDecoder.decode(this, "UTF-8")

private fun String.base64Decode(): String {
    val normalized = replace('-', '+').replace('_', '/').let {
        it + "=".repeat((4 - it.length % 4) % 4)
    }
    return String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
}

private fun String.base64DecodeOrNull(): String? =
    runCatching { base64Decode() }.getOrNull()
