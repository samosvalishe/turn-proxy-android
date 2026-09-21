package com.freeturn.app.data.config

/**
 * Determines whether a wg-quick configuration asks for AmneziaWG.
 *
 * The bundled core supports both `tunnel.mode = "wg"` and `"awg"`, but in
 * `wg` mode it deliberately discards every Amnezia parameter. Keep this list
 * in sync with `internal/tunnel/wgconf.applyInterface` in free-turn-proxy.
 */
internal fun String.usesAmneziaWireGuard(): Boolean {
    var inInterfaceSection = false

    for (rawLine in lineSequence()) {
        // Matches the bundled core parser: comments begin with '#' or ';'.
        val line = rawLine
            .substringBefore('#')
            .substringBefore(';')
            .trim()

        if (line.isEmpty()) continue

        if (line.startsWith('[') && line.endsWith(']')) {
            inInterfaceSection = line.equals("[Interface]", ignoreCase = true)
            continue
        }
        if (!inInterfaceSection) continue

        val separator = line.indexOf('=')
        if (separator < 0) continue
        val key = line.substring(0, separator).trim().lowercase()
        if (key in amneziaInterfaceKeys) return true
    }

    return false
}

private val amneziaInterfaceKeys = setOf(
    // AmneziaWG 1/2.
    "jc", "jmin", "jmax",
    "s1", "s2", "s3", "s4",
    "h1", "h2", "h3", "h4",
    "i1", "i2", "i3", "i4", "i5",
    // AmneziaWG 3+.
    "headerprotectionkey",
    "contentpaddingaddition",
    "rekeyaftertime",
    "rekeytimeout",
    "rejectaftertime",
    "keepalivetimeout",
    "maxhandshakeattempts",
    "randomtrailers",
    "disablecookies",
)
