package com.freeturn.app.ui.screens.home

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * Форматирует uptime прокси в «mm:ss» или «h:mm:ss», тикая раз в секунду.
 *
 * Источник времени — `SystemClock.elapsedRealtime()`, как и у `connectedSince`
 * в `ProxyServiceState`: это устойчиво к переводу системных часов (обычный
 * `System.currentTimeMillis()` при изменении времени показал бы отрицательные
 * или разорванные интервалы).
 *
 * Возвращает null, если прокси ни разу не подключался в текущей сессии.
 */
@Composable
internal fun rememberProxyUptime(connectedSince: Long?): String? {
    if (connectedSince == null) return null
    // Тик состояния, принудительно переформатирующий строку раз в секунду.
    // Пересоздаётся при смене connectedSince (новая сессия).
    val tick = produceState(initialValue = 0L, connectedSince) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val now = tick.value.coerceAtLeast(connectedSince)
    val totalSec = ((now - connectedSince) / 1_000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    // Ведущий ноль у минут, чтобы строка не прыгала при переходе 9:59 → 10:00.
    // Вместе с fontFeatureSettings="tnum" у Text это даёт полностью стабильную
    // ширину в первый час работы. Смена ширины остаётся только на переходе
    // 59:59 → 1:00:00 (раз за сессию) и 9:59:59 → 10:00:00.
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
