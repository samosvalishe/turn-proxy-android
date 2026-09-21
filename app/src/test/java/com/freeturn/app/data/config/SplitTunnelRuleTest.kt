package com.freeturn.app.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTunnelRuleTest {

    private val own = "com.freeturn.app"
    private val all: (String) -> Boolean = { true }

    private fun rule(
        mode: String,
        apps: String,
        hotspot: Boolean = false,
        isInstalled: (String) -> Boolean = all,
    ) = splitTunnelRule(mode, apps, own, hotspot, isInstalled)

    @Test
    fun `all mode touches nothing`() {
        assertEquals(SplitTunnelRule.All, rule(SplitTunnelMode.ALL, "a.b\nc.d"))
    }

    // Главный инвариант: пустой allow-список = "ни одного addAllowedApplication" =
    // туннель для всех. Правило обязано вернуть непустое множество.
    @Test
    fun `empty include never falls back to routing everything`() {
        assertEquals(SplitTunnelRule.Allowed(setOf(own)), rule(SplitTunnelMode.INCLUDE, ""))
    }

    @Test
    fun `include with only own package stays closed`() {
        assertEquals(SplitTunnelRule.Allowed(setOf(own)), rule(SplitTunnelMode.INCLUDE, own))
    }

    @Test
    fun `include with only uninstalled packages stays closed`() {
        val r = rule(SplitTunnelMode.INCLUDE, "a.b\nc.d", isInstalled = { false })
        assertEquals(SplitTunnelRule.Allowed(setOf(own)), r)
    }

    @Test
    fun `include drops own package and uninstalled ones`() {
        val r = rule(SplitTunnelMode.INCLUDE, "a.b\n$own\ngone.app", isInstalled = { it != "gone.app" })
        assertEquals(SplitTunnelRule.Allowed(setOf("a.b")), r)
    }

    @Test
    fun `include with hotspot adds own package`() {
        val r = rule(SplitTunnelMode.INCLUDE, "a.b", hotspot = true)
        assertEquals(SplitTunnelRule.Allowed(setOf("a.b", own)), r)
    }

    @Test
    fun `empty exclude falls back to default bypass list`() {
        val r = rule(SplitTunnelMode.EXCLUDE, "")
        assertEquals(SplitTunnelRule.Disallowed(DEFAULT_BYPASS_APPS), r)
    }

    @Test
    fun `exclude with hotspot keeps own package in tunnel`() {
        val r = rule(SplitTunnelMode.EXCLUDE, "a.b\n$own", hotspot = true)
        assertEquals(SplitTunnelRule.Disallowed(setOf("a.b")), r)
    }

    @Test
    fun `exclude without hotspot honours own package choice`() {
        val r = rule(SplitTunnelMode.EXCLUDE, "a.b\n$own")
        assertEquals(SplitTunnelRule.Disallowed(setOf("a.b", own)), r)
    }
}
