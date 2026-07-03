package com.freeturn.app.domain.server

import com.freeturn.app.data.control.ControlJson
import com.freeturn.app.data.control.ControlResponse

/** Машиночитаемый код ошибки control-скрипта (см. server-control ERR_CODE). */
enum class ServerErrorCode {
    BAD_ARG, NOT_WRITABLE, LOCK_FAILED, UNSUPPORTED_ARCH,
    NOT_INSTALLED, DOWNLOAD_FAILED, VERSION_RESOLVE_FAILED, NOT_ELF, TOO_SMALL, SHA_MISMATCH,
    WG_TOOLS_MISSING, WG_KERNEL_MISSING, WG_USERSPACE_MISSING, WG_UP_FAILED, WG_PORT_BUSY,
    NO_WG_BACKEND, PEER_ADD_FAILED, CONF_REWRITE_FAILED, NO_STORED_CONF, BASE64_MISSING,
    LISTEN_PORT_BUSY, START_FAILED, CLIENTS_CMD_FAILED,
    NEEDS_ROOT, SUDO_AUTH_FAILED, SUDO_REQUIRETTY, SUDO_UNAVAILABLE,
    TRANSPORT, INTERNAL, UNKNOWN;

    companion object {
        fun from(code: String?): ServerErrorCode {
            val c = code?.uppercase() ?: return UNKNOWN
            return entries.firstOrNull { it.name == c } ?: UNKNOWN
        }
    }
}

/** Ошибка серверной команды; [message] - текст для UI, [code] - для ветвления. */
class ServerCommandException(
    message: String,
    val code: ServerErrorCode = ServerErrorCode.UNKNOWN
) : Exception(message)

/** Текст ошибки + хвост LOG-строк для диагностики. */
fun ControlResponse.errorText(): String {
    val base = msg?.takeIf { it.isNotBlank() } ?: code ?: "unknown error"
    val tail = logs.takeLast(2).filter { it.isNotBlank() }
    return if (tail.isEmpty()) base else base + "\n" + tail.joinToString("\n")
}

fun ControlResponse.toFailure(): Result<Nothing> =
    Result.failure(ServerCommandException(errorText(), ServerErrorCode.from(code)))

fun ControlResponse.asUnit(): Result<Unit> =
    if (isOk) Result.success(Unit) else toFailure()

/** ok -> декодируем data в [T]; err -> failure с кодом/текстом. */
inline fun <reified T> ControlResponse.requireData(): Result<T> =
    if (isOk) runCatching { ControlJson.decode<T>(data) } else toFailure()
