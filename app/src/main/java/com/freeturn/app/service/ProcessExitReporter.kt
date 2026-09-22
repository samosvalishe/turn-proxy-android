package com.freeturn.app.service

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.freeturn.app.data.AppPreferences
import com.freeturn.app.domain.proxy.LogLevel
import com.freeturn.app.domain.proxy.ProxyLog
import kotlinx.coroutines.CancellationException
import java.time.Instant

internal suspend fun reportProcessExits(context: Context, prefs: AppPreferences, log: ProxyLog) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    try {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .sortedBy { it.timestamp }
        val reported = prefs.reportedProcessExits()
        val ids = exits.map { "${it.timestamp}:${it.pid}" }
        exits.zip(ids).filter { (_, id) -> id !in reported }.forEach { (exit, _) ->
            val description = exit.description.orEmpty().take(256)
                .replace('\n', ' ').replace('\r', ' ')
            log.add(
                "История завершений ОС: at=${Instant.ofEpochMilli(exit.timestamp)} " +
                    "pid=${exit.pid} reason=${exit.reason} status=${exit.status} " +
                    "importance=${exit.importance} description=$description"
            )
        }
        // Записи ОС могут появляться с задержкой; timestamp-watermark пропустил бы их.
        val retained = (reported + ids)
            .sortedBy { it.substringBefore(':').toLongOrNull() ?: 0L }
            .takeLast(32).toSet()
        if (retained != reported) prefs.setReportedProcessExits(retained)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.add("История завершений ОС недоступна: ${e.javaClass.simpleName}", LogLevel.Warning)
    }
}
