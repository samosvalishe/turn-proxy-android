package com.freeturn.app.data

/**
 * Разбор ручного DNS-списка (поле «DNS-серверы (вручную)»). Единственный источник истины
 * для движка (ProxyService → флаг `-dns-servers`) и UI (гейт свитча «DNS оператора»,
 * isError поля): если UI и движок нормализуют ввод по-разному, UI начинает врать про
 * приоритет — свитч погашен «ручным списком», который движок целиком отбросил.
 */
object DnsList {

    /** Токен похож на IPv4/IPv6/hostname. Грубый отсев мусора, не строгая валидация. */
    private val tokenRegex = Regex("^[0-9a-zA-Z.:_-]+$")

    /** Сырой ввод → токены. Разделители: запятая, точка с запятой, пробел. */
    fun tokens(raw: String): List<String> =
        raw.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    /** Comma-separated строка валидных токенов для `-dns-servers`; пусто — список не задан. */
    fun normalize(raw: String): String =
        tokens(raw).filter { it.matches(tokenRegex) }.joinToString(",")

    /** Есть ли во вводе токены, которые будут отброшены при нормализации. */
    fun hasInvalidTokens(raw: String): Boolean =
        tokens(raw).any { !it.matches(tokenRegex) }
}
