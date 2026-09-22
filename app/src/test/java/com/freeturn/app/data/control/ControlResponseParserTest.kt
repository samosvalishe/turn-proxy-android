package com.freeturn.app.data.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlResponseParserTest {

    @Test
    fun `parses ok probe envelope`() {
        val raw = """{"proto":3,"result":"ok","data":{"installed":true,"running":true,"euid":0,"arch":"x86_64","method":"docker","version":"latest","config":{"method":"docker","backend":"new","connect":"","host":"203.0.113.7","listen_port":56000,"wg_port":51820,"wg_net":"10.13.13.0/24","mode":"udp","obf_profile":"rtpopus3","obf_key":"ab","version":"latest"}},"logs":[]}"""
        val r = ControlResponseParser.parse(raw)
        assertTrue(r.isOk)
        val d = ControlJson.decode<ProbeData>(r.data)
        assertTrue(d.installed)
        assertEquals("docker", d.method)
        val c = d.config!!
        assertEquals("new", c.backend)
        assertEquals(56000, c.listenPort)
        assertEquals("10.13.13.0/24", c.wgNet)
        assertEquals("rtpopus3", c.obfProfile)
    }

    @Test
    fun `probe without install conf has no config`() {
        val raw = """{"proto":3,"result":"ok","data":{"installed":false,"running":false,"euid":0,"arch":"aarch64"},"logs":[]}"""
        val d = ControlJson.decode<ProbeData>(ControlResponseParser.parse(raw).data)
        assertFalse(d.installed)
        assertNull(d.config)
    }

    @Test
    fun `parses err envelope with code`() {
        val raw = """{"proto":3,"result":"err","code":"port_busy","msg":"порт 51820/udp занят","stage":"apply","logs":["x"]}"""
        val r = ControlResponseParser.parse(raw)
        assertFalse(r.isOk)
        assertEquals("port_busy", r.code)
        assertEquals("порт 51820/udp занят", r.msg)
    }

    @Test
    fun `other proto is proto_mismatch`() {
        val r = ControlResponseParser.parse("""{"proto":2,"result":"ok","data":{},"logs":[]}""")
        assertFalse(r.isOk)
        assertEquals(ControlResponseParser.PROTO_MISMATCH, r.code)
    }

    @Test
    fun `banner before json is tolerated`() {
        val raw = "Welcome to Ubuntu\nLast login: ...\n{\"proto\":3,\"result\":\"ok\",\"data\":{},\"logs\":[]}"
        assertTrue(ControlResponseParser.parse(raw).isOk)
    }

    @Test
    fun `transport failure maps to err`() {
        val r = ControlResponseParser.transportFailure("connection refused")
        assertFalse(r.isOk)
        assertEquals("transport", r.code)
        assertEquals("connection refused", r.msg)
    }

    @Test
    fun `ERROR prefix in output is kept as output`() {
        val r = ControlResponseParser.parse("ERROR: something printed by the host")
        assertFalse(r.isOk)
        assertEquals("ERROR: something printed by the host", r.msg)
    }

    // sudo пишет отказ в stderr, он в общем выводе (exec 2>&1) вместо JSON.
    @Test
    fun `sudo password failure detected`() {
        val r = ControlResponseParser.parse("sudo: a password is required")
        assertEquals("sudo_auth_failed", r.code)
    }

    @Test
    fun `non-json output is internal error`() {
        val r = ControlResponseParser.parse("bash: command not found")
        assertFalse(r.isOk)
    }

    @Test
    fun `client-list decodes clients and share`() {
        val raw = """{"proto":3,"result":"ok","data":{"clients":[{"name":"owner","client_id":"aa","self":true,"ip":"10.13.13.2","pub":"p=","hs":1700000000},{"name":"phone","client_id":"bb","self":false}],"share":{"backend":"new","host":"203.0.113.7","port":56000,"mode":"udp","obf_profile":"rtpopus3","obf_key":"k"}},"logs":[]}"""
        val d = ControlJson.decode<ClientListData>(ControlResponseParser.parse(raw).data)
        assertEquals(2, d.clients.size)
        assertTrue(d.clients[0].self)
        assertEquals("p=", d.clients[0].pub)
        assertEquals("", d.clients[1].pub)
        assertEquals("new", d.share.backend)
        assertEquals(56000, d.share.port)
    }

    @Test
    fun `apply decodes owner and key`() {
        val raw = """{"proto":3,"result":"ok","data":{"owner":{"client_id":"aa","pub":"p=","conf_b64":"W0ludGVyZmFjZV0=","link":"freeturn://x"},"obf_key":"kk","needs_restart":false},"logs":[]}"""
        val d = ControlJson.decode<ApplyData>(ControlResponseParser.parse(raw).data)
        assertEquals("aa", d.owner.clientId)
        assertEquals("[Interface]", decodeBase64(d.owner.confB64))
        assertEquals("kk", d.obfKey)
    }
}
