package com.freeturn.app.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Шина сырых freeturn://-строк на импорт. Три источника (deep link из
 * MainActivity, QR-скан, вставка из буфера) — один консьюмер (ImportViewModel).
 * CONFLATED: непрочитанная ссылка вытесняется новой, буфер не копится.
 */
class LinkImportBus {
    private val channel = Channel<String>(Channel.CONFLATED)

    fun offer(raw: String) {
        channel.trySend(raw)
    }

    val links: Flow<String> = channel.receiveAsFlow()
}
