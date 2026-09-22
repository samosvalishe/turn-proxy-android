package com.freeturn.app.domain.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Лог сессии: экранный буфер ([lines]) и файл ([LogFile]) за одним входом.
 *
 * Koin `single`: строки шлют из горутин Go, из сервиса и из UI, а буфер обязан быть
 * один на процесс - сервис переживает пересоздание, и его история не должна рваться.
 * Все методы потокобезопасны и не блокируют вызывающего: ядру нельзя тормозить
 * горутину ради записи на диск.
 */
class ProxyLog(dir: File) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val file = LogFile(dir)

    private val _lines = MutableStateFlow<List<LogEntry>>(emptyList())
    val lines: StateFlow<List<LogEntry>> = _lines.asStateFlow()

    private val seq = AtomicLong(0)
    private val buffer = ArrayDeque<LogEntry>()
    private val flushScheduled = AtomicBoolean(false)
    // Пишется из UI, читается из горутин Go.
    @Volatile private var enabled = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun add(message: String, level: LogLevel = LogLevel.Event) {
        // Файл ведём всегда: он нужен ровно тогда, когда экран логов был выключен, а
        // разбирать отвал уже поздно. Флаг гасит только вывод в UI.
        file.append(message, level)
        if (!enabled) return
        val entry = LogEntry(seq.getAndIncrement(), message, level)
        synchronized(buffer) {
            buffer.addLast(entry)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        scheduleFlush()
    }

    /** Только экран: файл ведёт историю через рестарты, ради которой он и заведён. */
    fun clearScreen() {
        synchronized(buffer) { buffer.clear() }
        _lines.value = emptyList()
    }

    /** Экран и файл. Явное действие пользователя - единственное, что стирает файл. */
    fun clearAll() {
        clearScreen()
        file.clear()
    }

    /** Собирает лог в [target] для отправки; false - писать было нечего. */
    suspend fun export(target: File): Boolean = file.export(target)

    /**
     * Ядро сыплет строками пачками из своих горутин: публикуем срез не чаще
     * [FLUSH_MS]. Флаг снимается до публикации - строка следом закажет новый флаш.
     */
    private fun scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(FLUSH_MS)
            flushScheduled.set(false)
            _lines.value = synchronized(buffer) { buffer.toList() }
        }
    }

    private companion object {
        const val MAX_LINES = 200
        const val FLUSH_MS = 120L
    }
}
