package com.freeturn.app.data.config

sealed interface SplitTunnelRule {
    data object All : SplitTunnelRule
    data class Allowed(val packages: Set<String>) : SplitTunnelRule
    data class Disallowed(val packages: Set<String>) : SplitTunnelRule
}

fun splitTunnelRule(
    mode: String,
    apps: String,
    ownPackage: String,
    hotspot: Boolean,
    isInstalled: (String) -> Boolean,
): SplitTunnelRule {
    if (mode != SplitTunnelMode.INCLUDE && mode != SplitTunnelMode.EXCLUDE) return SplitTunnelRule.All
    val packages = splitTunnelSelection(mode, apps).filter(isInstalled)
    return when (mode) {
        SplitTunnelMode.INCLUDE -> {
            val allowed =
                if (hotspot) packages + ownPackage
                else packages.filter { it != ownPackage }
            SplitTunnelRule.Allowed(allowed.toSet().ifEmpty { setOf(ownPackage) })
        }
        else -> SplitTunnelRule.Disallowed(
            packages.filterNot { hotspot && it == ownPackage }.toSet()
        )
    }
}
