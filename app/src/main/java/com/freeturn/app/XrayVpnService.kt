package com.freeturn.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.data.SplitTunnelMode
import com.freeturn.app.domain.XrayTunnelManager
import java.io.FileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class XrayVpnService : VpnService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferences
    private lateinit var xray: XrayTunnelManager
    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        xray = XrayTunnelManager(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_proxy), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        startForeground(
            NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_proxy_title))
                .setContentText(getString(R.string.notif_xray_vpn_connecting))
                .setSmallIcon(android.R.drawable.ic_menu_preferences)
                .setOngoing(true)
                .setContentIntent(openAppIntent)
                .build()
        )
        if (!ProxyServiceState.isRunning.value) {
            ProxyServiceState.setRunning(true)
            ProxyServiceState.clearLogs()
            ProxyServiceState.setStartupResult(null)
            ProxyServiceState.clearConnectedSince()
            ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
        }
        scope.launch { startVpnXray() }
        return START_STICKY
    }

    private suspend fun startVpnXray() {
        val cfg = prefs.clientConfigFlow.first()
        ProxyServiceState.setLogsEnabled(cfg.logsEnabled)
        runCatching {
            val vpn = Builder()
                .setSession("FreeTurn Xray")
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .applySplitTunnel(cfg.splitTunnelMode, cfg.splitTunnelApps, packageName)
                .establish()
                ?: error("VPN interface не создан")
            tun = vpn
            clearCloseOnExec(vpn.fileDescriptor)
            ProxyServiceState.addLog("Xray VPN: TUN fd=${vpn.fd}")
            xray.startDirectVpn(cfg, vpn.fd)
            ProxyServiceState.setStartupResult(StartupResult.Success)
            ProxyServiceState.setConnectionStats(ConnectionStats(1, 1))
            ProxyServiceState.markConnectedIfAbsent(android.os.SystemClock.elapsedRealtime())
            while (ProxyServiceState.isRunning.value && xray.isRunning) {
                delay(1_000)
            }
        }.onFailure { e ->
            val message = e.message ?: e.javaClass.simpleName
            ProxyServiceState.addLog("Xray VPN: ошибка запуска: $message")
            ProxyServiceState.setStartupResult(StartupResult.Failed("Xray VPN не запустился: $message"))
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProxyServiceState.addLog("Xray VPN: остановка")
        runBlocking { xray.stop() }
        tun?.close()
        tun = null
        ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
        ProxyServiceState.setRunning(false)
        ProxyServiceState.clearConnectedSince()
        scope.cancel()
    }

    private fun Builder.applySplitTunnel(
        mode: String,
        apps: String,
        selfPackage: String
    ): Builder {
        val packages = apps.toPackageList()
        when (mode) {
            SplitTunnelMode.INCLUDE -> {
                packages.filter { it != selfPackage }.forEach { runCatching { addAllowedApplication(it) } }
            }
            SplitTunnelMode.EXCLUDE -> {
                (packages + selfPackage).distinct().forEach { runCatching { addDisallowedApplication(it) } }
            }
            else -> runCatching { addDisallowedApplication(selfPackage) }
        }
        return this
    }

    private fun String.toPackageList(): List<String> =
        split(',', '\n', ' ', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun clearCloseOnExec(fd: FileDescriptor) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching {
            val flags = android.system.Os.fcntlInt(fd, android.system.OsConstants.F_GETFD, 0)
            android.system.Os.fcntlInt(
                fd,
                android.system.OsConstants.F_SETFD,
                flags and android.system.OsConstants.FD_CLOEXEC.inv()
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "XrayVpnChannel"
        private const val NOTIF_ID = 31
    }
}
