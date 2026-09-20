package com.freeturn.app.data.control

/** Парсит stdout control-команды в [ControlResponse] (один JSON-объект). */
object ControlResponseParser {

    fun parse(raw: String): ControlResponse {
        val text = raw.trim()
        if (text.isEmpty()) return err("internal", "empty output")

        // MOTD/banner/.bashrc или sudo-остаток могли попасть ПЕРЕД нашим
        // единственным JSON-объектом - берём последнюю строку, начинающуюся с '{'.
        val jsonLine = text.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
            ?: return err(transportCode(text), text.take(300))

        return runCatching {
            ControlJson.json.decodeFromString(ControlResponse.serializer(), jsonLine.trim())
        }.getOrElse {
            err("internal", "unparseable control output: ${jsonLine.take(200)}")
        }
    }

    fun transportFailure(message: String): ControlResponse = err(transportCode(message), message)

    private fun transportCode(msg: String): String = when {
        msg.contains("sudo", ignoreCase = true) && msg.contains("password", ignoreCase = true) -> "sudo_auth_failed"
        msg.contains("a terminal is required", ignoreCase = true) || msg.contains("requiretty", ignoreCase = true) -> "sudo_requiretty"
        else -> "transport"
    }

    private fun err(code: String, msg: String) =
        ControlResponse(proto = 2, result = "err", code = code, msg = msg)
}
