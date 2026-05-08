package com.freeturn.app.domain.server

/**
 * Результат выполнения серверной команды. Парсится из stdout `vk-turn-control.sh`.
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
