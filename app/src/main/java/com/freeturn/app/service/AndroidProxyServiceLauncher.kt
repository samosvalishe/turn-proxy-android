package com.freeturn.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.freeturn.app.domain.proxy.ProxyServiceLauncher

/** Поднимает/останавливает [ProxyService] как foreground-сервис. */
class AndroidProxyServiceLauncher(private val context: Context) : ProxyServiceLauncher {

    override fun start() {
        val intent = Intent(context, ProxyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stop() {
        context.stopService(Intent(context, ProxyService::class.java))
    }
}
