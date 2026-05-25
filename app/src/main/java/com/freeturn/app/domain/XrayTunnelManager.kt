package com.freeturn.app.domain

import android.content.Context
import android.os.Build
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.TunnelTransport
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class XrayTunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val process = AtomicReference<Process?>(null)

    val isRunning: Boolean
        get() = process.get()?.let { proc ->
            try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        } ?: false

    suspend fun startAfterProxyReady(cfg: ClientConfig) {
        if (cfg.tunnelTransport != TunnelTransport.VLESS) return
        start(cfg, rewriteOutboundEndpoint = true, tunFd = null)
    }

    suspend fun startDirect(cfg: ClientConfig) {
        if (cfg.tunnelTransport != TunnelTransport.VLESS) return
        start(cfg, rewriteOutboundEndpoint = false, tunFd = null)
    }

    suspend fun startDirectVpn(cfg: ClientConfig, tunFd: Int) {
        if (cfg.tunnelTransport != TunnelTransport.VLESS) return
        start(cfg, rewriteOutboundEndpoint = false, tunFd = tunFd)
    }

    private suspend fun start(
        cfg: ClientConfig,
        rewriteOutboundEndpoint: Boolean,
        tunFd: Int?
    ) {
        val inputConfig = cfg.xrayConfig.trim()
        if (inputConfig.isBlank()) {
            throw IllegalArgumentException("Xray config is empty")
        }
        val rawConfig = if (inputConfig.startsWith("{")) {
            inputConfig
        } else {
            XrayProfileImporter.import(inputConfig).client.xrayConfig
        }

        val executable = File(appContext.applicationInfo.nativeLibraryDir, "libxray.so")
        ProxyServiceState.addLog("Xray: nativeLibraryDir=${appContext.applicationInfo.nativeLibraryDir}")
        ProxyServiceState.addLog("Xray: supported ABIs=${Build.SUPPORTED_ABIS.joinToString(",")}")
        if (!executable.exists() || executable.length() <= 0L) {
            throw IllegalStateException(
                "libxray.so не найден в native libs (${Build.SUPPORTED_ABIS.joinToString(",")}). " +
                    "В APK сейчас есть arm64-v8a; нужен arm64-девайс или Xray core под ABI устройства."
            )
        }
        ProxyServiceState.addLog("Xray: core ${executable.absolutePath}")

        stop()

        val xrayDir = File(appContext.filesDir, "xray").apply { mkdirs() }
        val configFile = File(xrayDir, "config.json")
        val preparedConfig = when {
            tunFd != null -> rawConfig.withAndroidTunInbound()
            rewriteOutboundEndpoint -> {
                require(rawConfig.firstSupportedOutboundProtocol() == "vless") {
                    "для режима VLESS через FreeTurn нужен VLESS outbound. Для VMess/Trojan/Shadowsocks/Hysteria выберите режим Xray напрямую."
                }
                rawConfig.withFirstSupportedOutboundEndpoint(cfg.localPort.trim())
            }
            else -> rawConfig
        }
        withContext(Dispatchers.IO) {
            copyAssetIfPresent("xray/geoip.dat", File(xrayDir, "geoip.dat"))
            copyAssetIfPresent("xray/geosite.dat", File(xrayDir, "geosite.dat"))
            configFile.writeText(preparedConfig)
        }

        val proc = withContext(Dispatchers.IO) {
            startXrayProcess(executable, configFile, xrayDir, tunFd)
        }
        Thread {
            try {
                InputStreamReader(proc.inputStream).buffered().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) ProxyServiceState.addLog("Xray: $line")
                    }
                }
            } catch (_: Exception) {
            }
        }.apply {
            name = "xray-log-reader"
            isDaemon = true
            start()
        }
        val exitedImmediately = withContext(Dispatchers.IO) {
            proc.waitForCompat(700, TimeUnit.MILLISECONDS)
        }
        if (exitedImmediately) {
            throw IllegalStateException("Xray завершился сразу после запуска, проверьте config")
        }
        process.set(proc)
        ProxyServiceState.addLog(
            if (rewriteOutboundEndpoint) "Xray: ядро запущено через ${cfg.localPort.trim()}"
            else "Xray: ядро запущено напрямую"
        )
    }

    suspend fun stop() {
        val proc = process.getAndSet(null) ?: return
        withContext(Dispatchers.IO) {
            proc.destroy()
            if (!proc.waitForCompat(2, TimeUnit.SECONDS)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) proc.destroyForcibly()
                else proc.destroy()
            }
        }
        ProxyServiceState.addLog("Xray: ядро остановлено")
    }

    private fun copyAssetIfPresent(assetPath: String, target: File) {
        try {
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
        }
    }

    private fun startXrayProcess(
        executable: File,
        configFile: File,
        xrayDir: File,
        tunFd: Int?
    ): Process {
        val directArgs = listOf(executable.absolutePath, "-config", configFile.absolutePath)
        return try {
            processBuilder(directArgs, xrayDir, tunFd).start()
        } catch (e: IOException) {
            if (!e.message.orEmpty().contains("Permission denied", ignoreCase = true)) throw e
            val linker = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                "/system/bin/linker64"
            } else {
                "/system/bin/linker"
            }
            ProxyServiceState.addLog("Xray: прямой запуск запрещен, пробуем через $linker")
            processBuilder(listOf(linker) + directArgs, xrayDir, tunFd).start()
        }
    }

    private fun processBuilder(args: List<String>, xrayDir: File, tunFd: Int?): ProcessBuilder =
        ProcessBuilder(args)
            .redirectErrorStream(true)
            .directory(xrayDir)
            .also { pb ->
                pb.environment()["XRAY_LOCATION_ASSET"] = xrayDir.absolutePath
                pb.environment()["XRAY_LOCATION_CONFIG"] = xrayDir.absolutePath
                if (tunFd != null) {
                    pb.environment()["XRAY_TUN_FD"] = tunFd.toString()
                    pb.environment()["xray.tun.fd"] = tunFd.toString()
                }
            }
}

private fun Process.waitForCompat(timeout: Long, unit: TimeUnit): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return waitFor(timeout, unit)
    val deadline = System.currentTimeMillis() + unit.toMillis(timeout)
    while (System.currentTimeMillis() < deadline) {
        try {
            exitValue()
            return true
        } catch (_: IllegalThreadStateException) {
            Thread.sleep(100)
        }
    }
    return try {
        exitValue()
        true
    } catch (_: IllegalThreadStateException) {
        false
    }
}

private fun String.withFirstSupportedOutboundEndpoint(endpoint: String): String {
    val (host, port) = endpoint.parseHostPort()
    val root = JSONObject(this)
    val outbounds = root.optJSONArray("outbounds")
        ?: throw IllegalArgumentException("outbounds не найден в Xray config")
    for (i in 0 until outbounds.length()) {
        val outbound = outbounds.optJSONObject(i) ?: continue
        val protocol = outbound.optString("protocol").lowercase()
        if (protocol !in setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2")) {
            continue
        }
        val settings = outbound.optJSONObject("settings") ?: continue
        val vnext = settings.optJSONArray("vnext")
        if (vnext != null && vnext.length() > 0) {
            val first = vnext.optJSONObject(0) ?: continue
            first.put("address", host)
            first.put("port", port)
            return root.toString(2)
        }
        val servers = settings.optJSONArray("servers")
        if (servers != null && servers.length() > 0) {
            val first = servers.optJSONObject(0) ?: continue
            first.put("address", host)
            first.put("port", port)
            return root.toString(2)
        }
        settings.put("address", host)
        settings.put("port", port)
        return root.toString(2)
    }
    throw IllegalArgumentException("поддерживаемый outbound Xray не найден в config")
}

private fun String.firstSupportedOutboundProtocol(): String? {
    val outbounds = JSONObject(this).optJSONArray("outbounds") ?: return null
    for (i in 0 until outbounds.length()) {
        val protocol = outbounds.optJSONObject(i)?.optString("protocol")?.lowercase().orEmpty()
        if (protocol in setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2")) {
            return protocol
        }
    }
    return null
}

private fun String.withAndroidTunInbound(): String {
    val root = JSONObject(this)
    val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also {
        root.put("inbounds", it)
    }
    for (i in 0 until inbounds.length()) {
        if (inbounds.optJSONObject(i)?.optString("protocol").equals("tun", ignoreCase = true)) {
            return root.toString(2)
        }
    }
    val tunInbound = JSONObject()
        .put("tag", "android-tun")
        .put("protocol", "tun")
        .put("settings", JSONObject()
            .put("name", "xray0")
            .put("mtu", 1500)
            .put("gateway", org.json.JSONArray()
                .put("172.19.0.1/30")
                .put("fdfe:dcba:9876::1/126")
            )
            .put("dns", org.json.JSONArray()
                .put("1.1.1.1")
                .put("8.8.8.8")
            )
        )
    inbounds.put(0, tunInbound)
    return root.toString(2)
}

private fun String.parseHostPort(): Pair<String, Int> {
    val value = trim()
    val host = value.substringBeforeLast(":", "")
    val port = value.substringAfterLast(":", "").toIntOrNull()
    require(host.isNotBlank() && port != null && port in 1..65535) {
        "bad local TURN endpoint: $value"
    }
    return host to port
}
