package com.freeturn.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.freeturn.app.R
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.proxy.ProxyServiceLauncher
import com.freeturn.app.domain.proxy.ProxyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Единственная точка запуска/остановки [ProxyService] - для UI и внешних входов.
 * Здесь же персистится намерение пользователя: команда учтена, даже если сервис её
 * не получил (фон без права поднимать FGS), и переживает смерть процесса.
 */
class AndroidProxyServiceLauncher(
    private val context: Context,
    private val prefs: AppPreferences,
    private val store: ProxyStore
) : ProxyServiceLauncher {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatchScope =
        CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())

    @Volatile private var desired: Boolean? = null

    override fun start(): Job {
        prefs.setProxyDesired(true)
        desired = true
        store.starting()
        return dispatchScope.launch { sendStart() }
    }

    /**
     * Команда, а не `stopService`: пока `startForegroundService` ждёт в очереди,
     * останавливать нечего - `stopService` уходит впустую, и сервис поднимается уже
     * после отмены. Команда встаёт в ту же очередь и гасит его гарантированно.
     */
    override fun stop(): Job {
        prefs.setProxyDesired(false)
        desired = false
        store.idle()
        return dispatchScope.launch {
            try {
                context.startService(command(ProxyActions.STOP))
            } catch (_: Exception) {
                // Фон без права поднимать сервис - значит и поднимать уже нечего.
                context.stopService(Intent(context, ProxyService::class.java))
            }
        }
    }

    override fun restartIfRunning(): Job = dispatchScope.launch {
        if (desired == false || !store.status.value.busy) return@launch
        store.starting()
        sendStart()
    }

    private fun sendStart() {
        try {
            val intent = command(ProxyActions.START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) {
            store.fail(context.getString(R.string.proxy_error_service, e.detail()))
        }
    }

    private fun command(action: String) =
        Intent(context, ProxyService::class.java).setAction(action)
}
