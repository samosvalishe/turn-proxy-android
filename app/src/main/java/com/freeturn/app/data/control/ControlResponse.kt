package com.freeturn.app.data.control

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Base64

/**
 * Envelope протокола v2 control-скрипта (см. server-control/src/10-proto.sh).
 * Один JSON-объект на запуск: либо result=ok + data, либо result=err + code/msg.
 */
@Serializable
data class ControlResponse(
    val proto: Int = 0,
    val result: String = "",
    val code: String? = null,
    val msg: String? = null,
    val stage: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    val logs: List<String> = emptyList(),
) {
    val isOk: Boolean get() = result == "ok"
}

/** Единый Json для протокола: незнакомые ключи игнорим (forward-compat). */
object ControlJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    inline fun <reified T> decode(element: JsonElement): T = json.decodeFromJsonElement(element)
}

/** base64 -> UTF-8 текст; пусто/битое -> null. */
fun decodeBase64(s: String?): String? {
    if (s.isNullOrBlank()) return null
    return runCatching { String(Base64.getDecoder().decode(s.trim()), Charsets.UTF_8) }.getOrNull()
}
