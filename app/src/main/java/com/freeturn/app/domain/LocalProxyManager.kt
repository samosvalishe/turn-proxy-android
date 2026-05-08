package com.freeturn.app.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import com.freeturn.app.ConnectionStats
import com.freeturn.app.ProxyService
import com.freeturn.app.ProxyServiceState
import com.freeturn.app.StartupResult
import com.freeturn.app.data.ClientConfig
import com.freeturn.app.viewmodel.ProxyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalProxyManager(private val context: Context) {

    private val _proxyState = MutableStateFlow<ProxyState>(ProxyState.Idle)
    val proxyState: StateFlow<ProxyState> = _proxyState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var resetJob: kotlinx.coroutines.Job? = null

    suspend fun observeProxyLifecycle() {
        ProxyServiceState.proxyFailed.collect {
            setErrorWithAutoReset("Прокси упал ${ProxyService.MAX_RESTARTS} раз — проверьте настройки")
        }
    }

    suspend fun observeCaptchaEvents() {
        ProxyServiceState.captchaSession.collect { session ->
            if (session != null) {
                _proxyState.value = ProxyState.CaptchaRequired(session.url, session.sessionId)
            } else if (_proxyState.value is ProxyState.CaptchaRequired) {
                val s = ProxyServiceState.connectionStats.value
                _proxyState.value = if (s.active > 0) {
                    ProxyState.Running(s.active, s.total)
                } else {
                    ProxyState.Connecting(s.active, s.total)
                }
            }
        }
    }

    suspend fun observeProxyServiceStatus() {
        ProxyServiceState.isRunning.collect { running ->
            val current = _proxyState.value
            if (running) {
                if (current !is ProxyState.Running &&
                    current !is ProxyState.Connecting &&
                    current !is ProxyState.Starting) {
                    _proxyState.value = ProxyState.Starting
                }
            } else {
                if (current is ProxyState.Running ||
                    current is ProxyState.Connecting ||
                    current is ProxyState.Starting ||
                    current is ProxyState.CaptchaRequired) {
                    _proxyState.value = ProxyState.Idle
                }
            }
        }
    }

    /**
     * Переводит UI в Connecting/Running в зависимости от числа активных каналов.
     * - active == 0 + процесс жив → жёлтый (Connecting).
     * - active  > 0               → зелёный (Running).
     *
     * Captcha и Error имеют приоритет и не перезаписываются, пока активны.
     * Starting тоже не трогаем: он снимается StartupResult-логикой в startProxy.
     */
    suspend fun observeConnectionStats() {
        ProxyServiceState.connectionStats.collect { stats ->
            val current = _proxyState.value
            if (current is ProxyState.Error || current is ProxyState.CaptchaRequired) return@collect
            if (!ProxyServiceState.isRunning.value) return@collect

            val next: ProxyState = if (stats.active > 0) {
                ProxyState.Running(stats.active, stats.total)
            } else {
                ProxyState.Connecting(stats.active, stats.total)
            }

            if (current is ProxyState.Starting && stats.active == 0) return@collect
            _proxyState.value = next
        }
    }

    fun syncInitialState() {
        if (ProxyServiceState.isRunning.value) {
            val s = ProxyServiceState.connectionStats.value
            _proxyState.value = if (s.active > 0) {
                ProxyState.Running(s.active, s.total)
            } else {
                ProxyState.Connecting(s.active, s.total)
            }
        }
    }

    suspend fun startProxy(cfg: ClientConfig) {
        if (ProxyServiceState.isRunning.value) return
        if (_proxyState.value is ProxyState.Error) _proxyState.value = ProxyState.Idle

        if (!cfg.isRawMode && (cfg.serverAddress.isBlank() || cfg.vkLink.isBlank())) {
            setErrorWithAutoReset("Не заполнены настройки клиента")
            return
        }
        if (cfg.isRawMode && cfg.rawCommand.isBlank()) {
            setErrorWithAutoReset("Не задана raw-команда")
            return
        }

        _proxyState.value = ProxyState.Starting

        ProxyServiceState.clearLogs()
        ProxyServiceState.setStartupResult(null)
        ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
        ProxyServiceState.clearConnectedSince()
        val intent = Intent(context, ProxyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        // Умное ожидание стартапа. Watchdog внутри сервиса делает до MAX_RESTARTS
        // попыток с backoff до 30с — фиксированный таймаут (20с) обрывал UI ещё
        // на первой-второй ретрай-итерации. Теперь ждём пока:
        //   • сервис не отдаст StartupResult (Success/Failed), ИЛИ
        //   • сервис не остановится сам (watchdog исчерпал лимит → isRunning=false).
        // Верхняя граница в 5 минут — страховка от подвисшего сервиса.
        val result = withTimeoutOrNull(5 * 60_000L) {
            // Дождаться, что сервис фактически поднялся (onStartCommand).
            ProxyServiceState.isRunning.first { it }
            combine(
                ProxyServiceState.startupResult,
                ProxyServiceState.isRunning
            ) { sr, running -> sr to running }
                .first { (sr, running) -> sr != null || !running }
                .first
        }

        if (_proxyState.value is ProxyState.Error) return

        when (result) {
            null -> {
                stopProxy()
                setErrorWithAutoReset("Прокси не запустился")
            }
            is StartupResult.Failed -> {
                stopProxy()
                setErrorWithAutoReset(result.message)
            }
            is StartupResult.Success -> {
                val s = ProxyServiceState.connectionStats.value
                _proxyState.value = if (s.active > 0) {
                    ProxyState.Running(s.active, s.total)
                } else {
                    ProxyState.Connecting(s.active, s.total)
                }
            }
        }
    }

    fun stopProxy() {
        context.stopService(Intent(context, ProxyService::class.java))
        _proxyState.value = ProxyState.Idle
    }

    fun dismissCaptcha() {
        ProxyServiceState.setCaptchaSession(null)
        if (_proxyState.value is ProxyState.CaptchaRequired) {
            val s = ProxyServiceState.connectionStats.value
            _proxyState.value = if (s.active > 0) {
                ProxyState.Running(s.active, s.total)
            } else {
                ProxyState.Connecting(s.active, s.total)
            }
        }
    }

    fun setErrorWithAutoReset(message: String) {
        resetJob?.cancel()
        _proxyState.value = ProxyState.Error(message)
        resetJob = scope.launch {
            delay(4_000)
            if (_proxyState.value is ProxyState.Error) _proxyState.value = ProxyState.Idle
        }
    }

    fun clearState() {
        _proxyState.value = ProxyState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
