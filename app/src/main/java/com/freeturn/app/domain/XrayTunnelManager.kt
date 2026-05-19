package com.freeturn.app.domain

import android.content.Context
import android.os.Build
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.data.TunnelTransport
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class XrayTunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val process = AtomicReference<Process?>(null)

    suspend fun startAfterProxyReady(cfg: ClientConfig) {
        if (cfg.tunnelTransport != TunnelTransport.VLESS) return
        val rawConfig = cfg.xrayConfig.trim()
        if (rawConfig.isBlank()) {
            throw IllegalArgumentException("Xray config is empty")
        }

        val executable = File(appContext.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!executable.canExecute()) {
            throw IllegalStateException(
                "libxray.so не найден. Добавьте Xray core в app/src/main/jniLibs/<abi>/libxray.so"
            )
        }

        stop()

        val xrayDir = File(appContext.filesDir, "xray").apply { mkdirs() }
        val configFile = File(xrayDir, "config.json")
        val preparedConfig = rawConfig.withVlessEndpoint(cfg.localPort.trim())
        withContext(Dispatchers.IO) {
            copyAssetIfPresent("xray/geoip.dat", File(xrayDir, "geoip.dat"))
            copyAssetIfPresent("xray/geosite.dat", File(xrayDir, "geosite.dat"))
            configFile.writeText(preparedConfig)
        }

        val proc = withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(executable.absolutePath, "-config", configFile.absolutePath)
                .redirectErrorStream(true)
                .directory(xrayDir)
            pb.environment()["XRAY_LOCATION_ASSET"] = xrayDir.absolutePath
            pb.environment()["XRAY_LOCATION_CONFIG"] = xrayDir.absolutePath
            pb.start()
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
        ProxyServiceState.addLog("Xray: ядро запущено через ${cfg.localPort.trim()}")
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

private fun String.withVlessEndpoint(endpoint: String): String {
    val (host, port) = endpoint.parseHostPort()
    val root = JSONObject(this)
    val outbounds = root.optJSONArray("outbounds")
        ?: throw IllegalArgumentException("outbounds не найден в Xray config")
    for (i in 0 until outbounds.length()) {
        val outbound = outbounds.optJSONObject(i) ?: continue
        if (!outbound.optString("protocol").equals("vless", ignoreCase = true)) continue
        val settings = outbound.optJSONObject("settings") ?: continue
        val vnext = settings.optJSONArray("vnext") ?: continue
        val first = vnext.optJSONObject(0) ?: continue
        first.put("address", host)
        first.put("port", port)
        return root.toString(2)
    }
    throw IllegalArgumentException("VLESS outbound не найден в Xray config")
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
