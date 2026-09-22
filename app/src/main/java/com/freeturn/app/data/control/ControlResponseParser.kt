package com.freeturn.app.data.control

/** Парсит stdout RPC-команды в [ControlResponse] (один JSON-объект). */
object ControlResponseParser {

    fun parse(raw: String): ControlResponse {
        val text = raw.trim()
        if (text.isEmpty()) return err("internal", "empty output")

        // MOTD/banner/.bashrc или sudo-остаток могли попасть ПЕРЕД нашим
        // единственным JSON-объектом - берём последнюю строку, начинающуюся с '{'.
        val jsonLine = text.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
            ?: return err(transportCode(text), text.take(300))

        val r = runCatching {
            ControlJson.json.decodeFromString(ControlResponse.serializer(), jsonLine.trim())
        }.getOrElse {
            return err("internal", "unparseable control output: ${jsonLine.take(200)}")
        }
        if (r.proto != ControlResponse.PROTO_VERSION) {
            return err(PROTO_MISMATCH, "server proto ${r.proto}, app ${ControlResponse.PROTO_VERSION}")
        }
        return r
    }

    fun transportFailure(message: String, hostKeyChanged: Boolean = false): ControlResponse =
        err(if (hostKeyChanged) "host_key_changed" else transportCode(message), message)

    private fun transportCode(msg: String): String = when {
        msg.contains("sudo", ignoreCase = true) && msg.contains("password", ignoreCase = true) -> "sudo_auth_failed"
        msg.contains("a terminal is required", ignoreCase = true) || msg.contains("requiretty", ignoreCase = true) -> "sudo_requiretty"
        else -> "transport"
    }

    private fun err(code: String, msg: String) =
        ControlResponse(proto = ControlResponse.PROTO_VERSION, result = "err", code = code, msg = msg)

    const val PROTO_MISMATCH = "proto_mismatch"
}
