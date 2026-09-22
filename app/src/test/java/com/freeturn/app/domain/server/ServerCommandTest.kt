package com.freeturn.app.domain.server

import com.freeturn.app.data.config.KcpProfile
import com.freeturn.app.data.config.ObfProfile
import com.freeturn.app.data.config.ProxyMode
import com.freeturn.app.data.server.Server
import com.freeturn.app.data.server.ServerBackend
import com.freeturn.app.data.server.ServerMethod
import com.freeturn.app.data.server.ServerOpts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCommandTest {

    @Test
    fun `apply new backend sends wg and omits connect`() {
        val argv = ServerCommand.Apply(
            ApplyOptions(
                method = ServerMethod.SYSTEMD,
                backend = ServerBackend.NEW,
                listenPort = 56001,
                connect = "ignored:1",
                obfProfile = ObfProfile.RTPOPUS3
            )
        ).toArgv()
        assertEquals("apply", argv.first())
        assertTrue("--method=systemd" in argv)
        assertTrue("--listen-port=56001" in argv)
        assertTrue("--wg-net=${ServerBackend.DEFAULT_WG_NET}" in argv)
        assertFalse(argv.any { it.startsWith("--connect=") })
        // Пустой ключ не шлём - сервер оставит свой или сгенерирует.
        assertFalse(argv.any { it.startsWith("--obf-key=") })
        assertFalse(argv.any { it.startsWith("--kcp-") })
    }

    @Test
    fun `apply external tcp sends connect and full kcp`() {
        val argv = ApplyOptions(
            method = ServerMethod.DOCKER,
            backend = ServerBackend.EXTERNAL,
            listenPort = 56000,
            connect = "127.0.0.1:443",
            proxyMode = ProxyMode.TCP,
            kcp = KcpProfile.DEFAULT,
            obfProfile = ObfProfile.NONE,
            obfKey = "k".repeat(64)
        ).toArgv()
        assertTrue("--connect=127.0.0.1:443" in argv)
        assertFalse(argv.any { it.startsWith("--wg-") })
        assertFalse(argv.any { it.startsWith("--obf-key=") })
        assertEquals(8, argv.count { it.startsWith("--kcp-") })
    }

    @Test
    fun `apply update flags go after config`() {
        val opts = ApplyOptions(method = ServerMethod.DOCKER, backend = ServerBackend.NEW, listenPort = 56000)
        assertEquals(opts.toArgv(), ServerCommand.Apply(opts).toArgv())
        assertEquals(
            opts.toArgv() + listOf("--update", "--bin=/tmp/freeturn-server.x"),
            ServerCommand.Apply(opts, update = true, bin = "/tmp/freeturn-server.x").toArgv()
        )
    }

    @Test
    fun `uname maps to release asset`() {
        assertEquals("server-linux-amd64", ServerControl.serverAsset("x86_64"))
        assertEquals("server-linux-arm64", ServerControl.serverAsset("aarch64"))
        assertEquals("server-linux-armv7", ServerControl.serverAsset("armv7l"))
        assertEquals("server-linux-386", ServerControl.serverAsset("i686"))
        assertEquals(null, ServerControl.serverAsset("mips"))
    }

    @Test
    fun `server maps to apply options`() {
        val key = "a".repeat(64)
        val opts = Server(
            name = "s",
            proxyListen = "0.0.0.0:56123",
            proxyConnect = "10.0.0.1:51820",
            opts = ServerOpts(
                obfProfile = ObfProfile.RTPOPUS2,
                obfKey = key,
                backend = ServerBackend.EXTERNAL,
                method = ServerMethod.SYSTEMD
            )
        ).applyOptions()
        assertEquals(56123, opts.listenPort)
        assertEquals("10.0.0.1:51820", opts.connect)
        assertEquals(key, opts.obfKey)
        assertEquals(ServerMethod.SYSTEMD, opts.method)
    }

    @Test
    fun `client commands carry name`() {
        assertEquals(listOf("client-add", "--name=phone"), ServerCommand.ClientAdd("phone").toArgv())
        assertEquals(listOf("uninstall", "--target=all", "--purge"), ServerCommand.Uninstall.toArgv())
    }

    @Test
    fun `wg net validation mirrors install sh`() {
        assertTrue(ServerBackend.isValidNet("10.13.13.0/24"))
        assertTrue(ServerBackend.isValidNet("172.16.0.0/16"))
        assertFalse(ServerBackend.isValidNet("10.13.13.1/24"))
        assertFalse(ServerBackend.isValidNet("10.13.13.0/30"))
        assertFalse(ServerBackend.isValidNet("10.0.0.0/8"))
        assertFalse(ServerBackend.isValidNet("256.0.0.0/24"))
        assertFalse(ServerBackend.isValidNet("010.0.0.0/24"))
    }
}
