package com.freeturn.app

import android.app.Application
import android.content.pm.ApplicationInfo
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.di.appModule
import com.freeturn.app.domain.proxy.LogLevel
import com.freeturn.app.domain.proxy.ProxyEngine
import com.freeturn.app.domain.proxy.ProxyLog
import com.freeturn.app.domain.proxy.ProxyStore
import com.freeturn.app.service.ProxyNotifier
import com.freeturn.app.service.ProxyWidgetProvider
import com.freeturn.app.service.reportProcessExits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application() {

    private val appPreferences: AppPreferences by inject()
    private val engine: ProxyEngine by inject()
    private val store: ProxyStore by inject()
    private val log: ProxyLog by inject()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // ed25519/curve25519 работает через Bouncy Castle в classpath. jsch 2.x подхватывает его сам.
        startKoin {
            androidLogger(if (debuggable()) Level.DEBUG else Level.NONE)
            androidContext(this@App)
            modules(appModule)
        }

        log.add("Процесс запущен pid=${android.os.Process.myPid()}")
        // Раз за процесс: в onCreate сервиса эти транзакции доставались главному потоку
        // ровно на нажатии кнопки.
        ProxyNotifier.createChannels(this)
        reportPreviousExit()
        warmUpCore()
        observeLogsEnabled()
        observeWidgetState()
    }

    private fun observeLogsEnabled() {
        appPreferences.clientConfigFlow
            .map { it.logsEnabled }
            .distinctUntilChanged()
            .onEach(log::setEnabled)
            .launchIn(scope)
    }

    // Незакрытая сессия не доказывает причину завершения процесса.
    private fun reportPreviousExit() {
        scope.launch(Dispatchers.IO) {
            reportProcessExits(this@App, appPreferences, log)
            if (!appPreferences.previousSessionUnclean()) return@launch
            log.add("Предыдущая сессия завершилась без штатной остановки", LogLevel.Warning)
        }
    }

    // Первое обращение к ядру грузит нативную библиотеку и поднимает Go-runtime -
    // без прогрева эта задержка достаётся первому нажатию "Запустить".
    private fun warmUpCore() {
        scope.launch(Dispatchers.IO) {
            runCatching { engine.version }
                // Обычно это провал загрузки нативной библиотеки - запуск всё равно
                // упадёт, но уже без внятной причины в логе.
                .onFailure { log.add("Ядро не загрузилось: ${it.message}", LogLevel.Error) }
        }
    }

    private fun observeWidgetState() {
        combine(
            store.status,
            appPreferences.serversSnapshot
        ) { status, snap ->
            listOf(status.busy, status.phase, status.active, status.total, snap.active?.name)
        }
            .distinctUntilChanged()
            .conflate()
            .onEach {
                ProxyWidgetProvider.refresh(this)
                delay(WIDGET_REFRESH_MIN_MS)
            }
            .launchIn(scope)
    }

    private fun debuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private companion object {
        const val WIDGET_REFRESH_MIN_MS = 2_000L
    }
}
