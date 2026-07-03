package com.freeturn.app

import android.app.Application
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.di.appModule
import com.freeturn.app.domain.proxy.ProxyServiceState
import com.freeturn.app.service.ProxyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class App : Application() {

    private val appPreferences: AppPreferences by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // ed25519/curve25519 работает через Bouncy Castle в classpath. jsch 2.x подхватывает его сам.
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
        observeWidgetState()
    }

    // Перерисовывает виджет при смене статуса прокси или активного сервера
    // (RemoteViews не реактивны - их надо толкать вручную).
    private fun observeWidgetState() {
        combine(
            ProxyServiceState.isRunning,
            ProxyServiceState.connectionStats,
            appPreferences.serversSnapshot
        ) { running, stats, snap ->
            listOf(running, stats.active, stats.total, snap.active?.name)
        }
            .distinctUntilChanged()
            .onEach { ProxyWidgetProvider.refresh(this) }
            .launchIn(scope)
    }
}
