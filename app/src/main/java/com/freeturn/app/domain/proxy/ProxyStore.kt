package com.freeturn.app.domain.proxy

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Состояние прокси: пишут ядро ([ProxyEngine]) и хост-сервис, читают UI и внешние
 * точки входа (тайл, виджет, трамплин ярлыков).
 *
 * Koin `single`, а не `object`: сервис и ресиверы живут вне графа, но достают его тем же
 * `inject()`, что и всё остальное - иначе любое место в приложении могло дотянуться до
 * состояния чужой сессии (см. [coreErrors]). Единственность на процесс даёт сам Koin,
 * поэтому пересоздание сервиса состояние не роняет.
 */
class ProxyStore {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _status = MutableStateFlow(ProxyStatus())
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    // Ошибка именно ОТ ЯДРА, а не любая красная фаза: fail() зовут и снаружи сессии
    // (отказ от VPN-согласия в трамплине, отлуп startForegroundService), а хост по такой
    // ошибке сворачивал бы живую чужую сессию.
    private val _coreErrors = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val coreErrors: SharedFlow<String> = _coreErrors.asSharedFlow()

    private val captchaSeq = AtomicLong(0)
    // Поколение показанной ошибки: таймер гасит только свою. Ошибки приходят из горутин
    // Go, сервиса и UI - хранить Job и отменять его было гонкой, а ошибку от ядра,
    // пришедшую следом за fail(), чужой таймер сбрасывал досрочно.
    private val errorSeq = AtomicLong(0)

    /** Команда старта принята: кнопка реагирует до того, как поднимется сервис. */
    fun starting() {
        errorSeq.incrementAndGet()
        _status.value = ProxyStatus(phase = ProxyPhase.Starting)
    }

    fun idle() {
        errorSeq.incrementAndGet()
        _status.value = ProxyStatus()
    }

    /** Сервис доломан. Ошибку не затираем - её ещё показывает кнопка. */
    fun finish() {
        if (_status.value.phase != ProxyPhase.Error) idle()
    }

    /** Сессия не состоялась. Ошибка сама гаснет - иначе кнопка залипает в красном. */
    fun fail(message: String) {
        val generation = errorSeq.incrementAndGet()
        _status.value = ProxyStatus(phase = ProxyPhase.Error, error = message)
        scheduleErrorReset(generation)
    }

    /** Фаза от ядра. Момент подключения ставится один раз - рестарт его не сбивает. */
    fun setPhase(phase: ProxyPhase, active: Int, total: Int, error: String = "") {
        if (phase == ProxyPhase.Error) {
            scheduleErrorReset(errorSeq.incrementAndGet())
            _coreErrors.tryEmit(error)
        }
        _status.update {
            val connected = if (phase == ProxyPhase.Connected) {
                it.connectedSince ?: SystemClock.elapsedRealtime()
            } else {
                it.connectedSince
            }
            it.copy(phase = phase, active = active, total = total, error = error, connectedSince = connected)
        }
    }

    fun setTunnelUp(up: Boolean) {
        _status.update { it.copy(tunnelUp = up) }
    }

    /** Пустой [url] - капча снята. Фазу ведёт ядро, здесь только адрес окна. */
    fun setCaptcha(url: String) {
        _status.update {
            if (url.isBlank()) it.copy(captchaUrl = "")
            else it.copy(captchaUrl = url, captchaId = captchaSeq.incrementAndGet())
        }
    }

    private fun scheduleErrorReset(generation: Long) {
        scope.launch {
            delay(ERROR_RESET_MS)
            if (errorSeq.get() == generation && _status.value.phase == ProxyPhase.Error) idle()
        }
    }

    private companion object {
        const val ERROR_RESET_MS = 4_000L
    }
}
