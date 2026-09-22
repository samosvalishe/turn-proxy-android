package com.freeturn.app.domain.proxy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Лог сессии на диске. Экранный буфер живёт в памяти и чистится на каждом старте, поэтому
 * разобрать инцидент, после которого пользователь перезапустил прокси, было нечем.
 *
 * Пишет отдельным потоком: строки приходят из горутин Go, блокировать их нельзя. Время
 * форматируется там же - SimpleDateFormat не потокобезопасен. Очистка и экспорт идут той
 * же очередью: ротация и запись не пересекаются с чтением файлов.
 */
internal class LogFile(dir: File) {

    private sealed interface Cmd {
        data class Line(val at: Long, val text: String, val level: LogLevel) : Cmd
        data object Clear : Cmd
        class Export(val target: File, val done: CompletableDeferred<Boolean>) : Cmd
    }

    private val current = File(dir, "session.log")
    private val previous = File(dir, "session.1.log")
    private val queue = Channel<Cmd>(QUEUE_CAPACITY)
    private val dropped = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        dir.mkdirs()
        scope.launch { drain() }
    }

    fun append(message: String, level: LogLevel) {
        if (queue.trySend(Cmd.Line(System.currentTimeMillis(), message, level)).isFailure) {
            dropped.incrementAndGet()
        }
    }

    // Сразу в очередь, если есть место: строки, отправленные до очистки, должны встать до неё.
    fun clear() {
        if (queue.trySend(Cmd.Clear).isFailure) scope.launch { queue.send(Cmd.Clear) }
    }

    /** Оба файла подряд, старый первым - хронология не должна рваться в середине. */
    suspend fun export(target: File): Boolean {
        val done = CompletableDeferred<Boolean>()
        queue.send(Cmd.Export(target, done))
        return done.await()
    }

    // Пачкой: ядро сыплет строками из нескольких горутин, открывать файл на каждую дорого.
    private suspend fun drain() {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        val batch = StringBuilder()
        for (cmd in queue) {
            apply(cmd, stamp, batch)
            while (batch.length < MAX_BATCH_CHARS) {
                val next = queue.tryReceive().getOrNull() ?: break
                apply(next, stamp, batch)
            }
            flush(stamp, batch)
        }
    }

    private fun apply(cmd: Cmd, stamp: SimpleDateFormat, batch: StringBuilder) {
        when (cmd) {
            is Cmd.Line -> batch.appendEntry(stamp, cmd.at, cmd.level, cmd.text)
            is Cmd.Clear -> {
                batch.setLength(0)
                runCatching { current.delete(); previous.delete() }
            }
            is Cmd.Export -> {
                flush(stamp, batch)
                cmd.done.complete(exportTo(cmd.target))
            }
        }
    }

    private fun flush(stamp: SimpleDateFormat, batch: StringBuilder) {
        val lost = dropped.getAndSet(0)
        if (lost > 0) {
            val note = "Лог: потеряно $lost строк (переполнение очереди)"
            batch.appendEntry(stamp, System.currentTimeMillis(), LogLevel.Warning, note)
        }
        if (batch.isEmpty()) return
        write(batch.toString())
        batch.setLength(0)
    }

    private fun exportTo(target: File): Boolean = runCatching {
        target.outputStream().buffered().use { out ->
            listOf(previous, current).filter { it.exists() }.forEach { part ->
                part.inputStream().use { it.copyTo(out) }
            }
        }
        target.length() > 0
    }.getOrDefault(false)

    private fun write(text: String) {
        runCatching {
            if (current.length() > MAX_BYTES) {
                previous.delete()
                current.renameTo(previous)
            }
            current.appendText(text)
        }
    }

    private fun StringBuilder.appendEntry(stamp: SimpleDateFormat, at: Long, level: LogLevel, text: String) {
        append(stamp.format(Date(at))).append(' ')
            .append(level.tag()).append(' ')
            .append(text).append('\n')
    }

    private fun LogLevel.tag(): String = when (this) {
        LogLevel.Error -> "E"
        LogLevel.Warning -> "W"
        LogLevel.Event -> "*"
        LogLevel.Plain -> " "
    }

    private companion object {
        const val MAX_BYTES = 1_000_000L
        const val QUEUE_CAPACITY = 4_096
        const val MAX_BATCH_CHARS = 256 * 1024
    }
}
