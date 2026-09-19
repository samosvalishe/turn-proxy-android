package com.freeturn.app.data

/** Разбор argv-строки от ядра (`Mobile.configToArgs`) для показа пользователю. */
object CoreCommand {

    private val SENSITIVE_FLAGS = setOf("-peer", "-links", "-obf-key", "-turn", "-client-id", "-sub")

    private const val MASK = "••••••"

    fun redact(commandLine: String, privacy: Boolean): String {
        if (!privacy) return commandLine
        // По \s+, а не по одному пробелу: лишний разделитель сдвинул бы пары флаг/значение
        // и в лог, который пользователь шарит, уехал бы хвост секрета.
        val tokens = commandLine.trim().split(Regex("\\s+"))
        return buildString {
            var i = 0
            while (i < tokens.size) {
                val tok = tokens[i]
                if (isNotEmpty()) append(' ')
                append(tok)
                if (tok in SENSITIVE_FLAGS && i + 1 < tokens.size) {
                    append(' ').append(MASK)
                    i += 2
                } else {
                    i++
                }
            }
        }
    }
}
