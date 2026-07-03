package com.freeturn.app.data.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlResponseParserTest {

    @Test
    fun `parses ok probe envelope`() {
        val raw = """{"proto":2,"result":"ok","data":{"installed":true,"running":false,"runtime":"systemd","euid":0,"wg":{"present":true,"port":51820},"virt":"kvm","wg_kernel":true,"conflicts":{"warp":true,"x3ui":false,"wgeasy":false,"tailscale":false,"other_ifaces":["CloudflareWARP"]}},"logs":[]}"""
        val r = ControlResponseParser.parse(raw)
        assertTrue(r.isOk)
        assertEquals(2, r.proto)
        val d = ControlJson.decode<ProbeData>(r.data)
        assertTrue(d.installed)
        assertEquals("systemd", d.runtime)
        assertEquals(51820, d.wg.port)
        assertTrue(d.conflicts.warp)
        assertEquals(listOf("CloudflareWARP"), d.conflicts.otherIfaces)
    }

    @Test
    fun `wg port null decodes to null`() {
        val raw = """{"proto":2,"result":"ok","data":{"wg":{"present":false,"port":null}},"logs":[]}"""
        val d = ControlJson.decode<ProbeData>(ControlResponseParser.parse(raw).data)
        assertFalse(d.wg.present)
        assertNull(d.wg.port)
    }

    @Test
    fun `parses err envelope with code`() {
        val raw = """{"proto":2,"result":"err","code":"wg_port_busy","msg":"udp 51820 busy","stage":"wg_setup","logs":["x"]}"""
        val r = ControlResponseParser.parse(raw)
        assertFalse(r.isOk)
        assertEquals("wg_port_busy", r.code)
        assertEquals("udp 51820 busy", r.msg)
    }

    @Test
    fun `banner before json is tolerated`() {
        val raw = "Welcome to Ubuntu\nLast login: ...\n{\"proto\":2,\"result\":\"ok\",\"data\":{},\"logs\":[]}"
        assertTrue(ControlResponseParser.parse(raw).isOk)
    }

    @Test
    fun `transport ERROR prefix maps to err`() {
        val r = ControlResponseParser.parse("ERROR: connection refused")
        assertFalse(r.isOk)
        assertEquals("transport", r.code)
        assertEquals("connection refused", r.msg)
    }

    @Test
    fun `sudo password failure detected`() {
        val r = ControlResponseParser.parse("ERROR: sudo: a password is required")
        assertEquals("sudo_auth_failed", r.code)
    }

    @Test
    fun `non-json output is internal error`() {
        val r = ControlResponseParser.parse("bash: command not found")
        assertFalse(r.isOk)
    }

    @Test
    fun `share-list arrays decode`() {
        val raw = """{"proto":2,"result":"ok","data":{"peers":[{"pub":"a=","ip":"10.0.0.2","has_conf":true}],"self_pub":"a=","clients":[{"id":"deadbeef"}]},"logs":[]}"""
        val d = ControlJson.decode<ShareListData>(ControlResponseParser.parse(raw).data)
        assertEquals(1, d.peers.size)
        assertEquals("a=", d.peers[0].pub)
        assertTrue(d.peers[0].hasConf)
        assertEquals("deadbeef", d.clients[0].id)
    }
}
