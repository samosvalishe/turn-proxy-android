package com.freeturn.app.viewmodel

import android.content.Context
import com.freeturn.app.R
import com.freeturn.app.domain.server.ServerCommandException
import com.freeturn.app.domain.server.ServerErrorCode

/** Текст ошибки для UI: свой текст для известных кодов, иначе message исключения. */
fun Throwable.uiError(context: Context): String {
    val res = when ((this as? ServerCommandException)?.code) {
        ServerErrorCode.PROTO_MISMATCH -> R.string.error_server_proto
        ServerErrorCode.HOST_KEY_CHANGED -> R.string.error_ssh_host_key_changed
        else -> null
    }
    if (res != null) return context.getString(res)
    return message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_ssh_generic)
}
