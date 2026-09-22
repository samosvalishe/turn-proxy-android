package com.freeturn.app.data.server

import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.ProxyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerJsonTest {

    @Test
    fun `opts round trip keeps mode and arq`() {
        val srv = Server(
            name = "s",
            opts = ServerOpts(
                proxyMode = ProxyMode.TCP,
                kcp = KcpProfile.MOBILE
            )
        )
        val decoded = ServerJson.decodeList(ServerJson.encodeList(listOf(srv))).single()
        assertEquals(ProxyMode.TCP, decoded.opts.proxyMode)
        assertEquals(KcpProfile.MOBILE, decoded.opts.kcp)
    }

    @Test
    fun `install opts round trip`() {
        val srv = Server(
            name = "s",
            opts = ServerOpts(
                method = ServerMethod.SYSTEMD,
                backend = ServerBackend.EXTERNAL,
                wgPort = 51999,
                wgNet = "10.20.0.0/16"
            )
        )
        val o = ServerJson.decodeList(ServerJson.encodeList(listOf(srv))).single().opts
        assertEquals(ServerMethod.SYSTEMD, o.method)
        assertEquals(ServerBackend.EXTERNAL, o.backend)
        assertEquals(51999, o.wgPort)
        assertEquals("10.20.0.0/16", o.wgNet)
    }

    @Test
    fun `snapshot without install opts falls back to defaults`() {
        val raw = """[{"id":"1","name":"s","opts":{"backend":"bogus"}}]"""
        val o = ServerJson.decodeList(raw).single().opts
        assertEquals(ServerMethod.DOCKER, o.method)
        assertEquals(ServerBackend.NEW, o.backend)
        assertEquals(ServerBackend.DEFAULT_WG_NET, o.wgNet)
    }

    @Test
    fun `snapshot without mode and kcp falls back to defaults`() {
        val raw = """[{"id":"1","name":"s","opts":{"obfProfile":"none","obfKey":""}}]"""
        val decoded = ServerJson.decodeList(raw).single()
        assertEquals(ProxyMode.UDP, decoded.opts.proxyMode)
        assertEquals(KcpProfile.DEFAULT, decoded.opts.kcp)
    }

    @Test
    fun `obf timing round trip and clamp`() {
        val srv = Server(name = "s", opts = ServerOpts(obfTimingMs = 20))
        assertEquals(20, ServerJson.decodeList(ServerJson.encodeList(listOf(srv))).single().opts.obfTimingMs)

        val raw = """[{"id":"1","name":"s","opts":{"obfTimingMs":9000}}]"""
        assertEquals(
            ObfProfile.TIMING_MAX,
            ServerJson.decodeList(raw).single().opts.obfTimingMs
        )
    }

    @Test
    fun `unknown mode falls back to udp`() {
        val raw = """[{"id":"1","name":"s","opts":{"proxyMode":"quic"}}]"""
        assertEquals(ProxyMode.UDP, ServerJson.decodeList(raw).single().opts.proxyMode)
    }

    // null запрещает запись поверх: пустой список + правка стирали все серверы.
    @Test
    fun `unparseable list is null, not empty`() {
        assertNull(ServerJson.decodeListOrNull("{broken"))
        assertEquals(emptyList<Server>(), ServerJson.decodeListOrNull(null))
    }

    @Test
    fun `junk element does not drop the rest`() {
        val raw = """[42,{"id":"1","name":"s"}]"""
        assertEquals("1", ServerJson.decodeListOrNull(raw)!!.single().id)
    }
}
