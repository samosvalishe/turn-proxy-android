package com.freeturn.app.service

import android.net.TrafficStats
import android.os.Process
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Опрашивает TrafficStats по UID раз в 3с и отдаёт строку "↓ rx ↑ tx" в [onSpeed].
 * Цикл живёт пока [isStopped] не вернёт true (или scope не отменят).
 */
class SpeedMonitor(
    private val scope: CoroutineScope,
    private val isStopped: () -> Boolean,
    private val onSpeed: (String) -> Unit,
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            val uid = Process.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            while (!isStopped()) {
                delay(3000)
                val currentRx = TrafficStats.getUidRxBytes(uid)
                val currentTx = TrafficStats.getUidTxBytes(uid)
                if (currentRx != TrafficStats.UNSUPPORTED.toLong() &&
                    lastRx != TrafficStats.UNSUPPORTED.toLong()) {
                    val rxSpeed = maxOf(0, currentRx - lastRx)
                    val txSpeed = maxOf(0, currentTx - lastTx)
                    onSpeed("↓ ${format(rxSpeed)} ↑ ${format(txSpeed)}")
                    lastRx = currentRx
                    lastTx = currentTx
                }
            }
        }
    }

    private fun format(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B/s"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB/s"
        else -> String.format(Locale.US, "%.1f MB/s", bytes / (1024f * 1024f))
    }
}
