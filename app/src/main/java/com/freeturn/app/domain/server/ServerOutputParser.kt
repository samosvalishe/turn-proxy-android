package com.freeturn.app.domain.server

/**
 * Результат выполнения серверной команды. Парсится из stdout `free-turn-control.sh`.
 *
 * Контракт скрипта:
 * - Каждая строка вида `KEY=VALUE` идёт в [Ok.kv].
 * - Префикс `LOG: ` — свободный текст, попадает в [Ok.logs] / [Err.logs] без префикса.
 * - Прочие непустые строки трактуются как лог.
 * - Финальная строка обязана быть `RESULT=ok` или `RESULT=err`. При `err`
 *   ожидается `ERR=<message>` ранее в выводе.
 *
 * Если на верхнем уровне `SSHManager` вернул `ERROR: ...` (сетевая/MITM ошибка) —
 * это перехватывается до парсера: [parse] также трактует такой случай как [Err].
 */
sealed class CmdResult {
    abstract val logs: List<String>
    data class Ok(val kv: Map<String, String>, override val logs: List<String>) : CmdResult()
    data class Err(val message: String, override val logs: List<String>) : CmdResult()
}

object ServerOutputParser {
    fun parse(raw: String): CmdResult {
        // SSHManager сигнализирует транспортные ошибки префиксом "ERROR: ".
        // Не пытаемся разбирать как k=v — отдаём наверх как есть.
        if (raw.startsWith("ERROR:")) {
            return CmdResult.Err(raw.removePrefix("ERROR:").trim(), emptyList())
        }
        val kv = LinkedHashMap<String, String>()
        val logs = mutableListOf<String>()
        raw.lines().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isEmpty() -> { /* skip */ }
                line.startsWith("LOG: ") -> logs += line.removePrefix("LOG: ")
                // k=v: ключ — только [A-Z0-9_], всё остальное считаем логом.
                line.contains('=') && line.substringBefore('=').matches(Regex("[A-Z][A-Z0-9_]*")) -> {
                    val k = line.substringBefore('=')
                    val v = line.substringAfter('=')
                    kv[k] = v
                }
                else -> logs += line
            }
        }
        return when (kv["RESULT"]) {
            "ok"  -> CmdResult.Ok(kv, logs)
            "err" -> CmdResult.Err(kv["ERR"] ?: "unknown error", logs)
            else  -> CmdResult.Err(
                "no RESULT marker (output truncated?)",
                logs
            )
        }
    }
}

/** Ошибка серверной команды; message — текст из скрипта/SSH для UI. */
class ServerCommandException(message: String) : Exception(message)

/** Текст ошибки + хвост LOG-строк скрипта: без него «wg-quick failed» недиагностируем. */
fun CmdResult.Err.errMessage(): String {
    val tail = logs.takeLast(2).filter { it.isNotBlank() }
    return if (tail.isEmpty()) message else message + "\n" + tail.joinToString("\n")
}

fun CmdResult.Err.toFailure(): Result<Nothing> =
    Result.failure(ServerCommandException(errMessage()))

fun CmdResult.asUnit(): Result<Unit> = when (this) {
    is CmdResult.Ok -> Result.success(Unit)
    is CmdResult.Err -> toFailure()
}

/** base64 → UTF-8 текст; битое значение → null. */
fun decodeBase64(s: String): String? =
    runCatching { String(java.util.Base64.getDecoder().decode(s.trim()), Charsets.UTF_8) }.getOrNull()
