package com.freeturn.app.ui.screens.home

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

// elapsedRealtime сохраняет корректный интервал при переводе системных часов.
@Composable
internal fun rememberProxyUptime(connectedSince: Long?): String? {
    if (connectedSince == null) return null
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
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
