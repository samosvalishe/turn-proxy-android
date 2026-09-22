package com.freeturn.app.domain.server

import com.freeturn.app.data.control.ApplyData
import com.freeturn.app.data.control.ControlJson
import com.freeturn.app.data.control.ControlResponse
import com.freeturn.app.data.control.decodeBase64

/** Код ошибки RPC install.sh (список - в шапке скрипта) плюс транспортные коды приложения. */
enum class ServerErrorCode {
    BAD_ARG, NEEDS_ROOT, NOT_INSTALLED, UNSUPPORTED_ARCH, NO_SYSTEMD, DOCKER_FAILED,
    DOWNLOAD_FAILED, WG_UNSUPPORTED, WG_INSTALL_FAILED, WG_CONFLICT, BACKEND_LOCKED, PORT_BUSY,
    SUBNET_CONFLICT, SUBNET_FULL, HOST_UNKNOWN, EXISTS, NOT_FOUND, OWNER_PROTECTED, START_FAILED,
    SUDO_AUTH_FAILED, SUDO_REQUIRETTY, PROTO_MISMATCH, HOST_KEY_CHANGED,
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

fun ControlResponse.applyResult(): Result<ApplyResult> =
    requireData<ApplyData>().map {
        ApplyResult(it.owner.clientId, decodeBase64(it.owner.confB64).orEmpty(), it.obfKey)
    }
