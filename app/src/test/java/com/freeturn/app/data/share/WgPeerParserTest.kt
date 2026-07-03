package com.freeturn.app.data.share

import com.freeturn.app.data.control.ClientDto
import com.freeturn.app.data.control.PeerDto
import com.freeturn.app.data.control.ShareListData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WgPeerParserTest {

    private fun nameB64(name: String): String =
        Base64.getEncoder().encodeToString(name.toByteArray(Charsets.UTF_8))

    @Test
    fun `maps peers with names handshake and conf flags`() {
        val data = ShareListData(
            selfPub = "selfkey=",
            peers = listOf(
                PeerDto(pub = "selfkey=", ip = "10.13.13.2", hs = 1_760_000_000L, hasConf = false),
                PeerDto(pub = "guestkey=", nameB64 = nameB64("Папа"), ip = "10.13.13.3", hs = 0, hasConf = true),
            ),
        )
        val peers = WgPeerParser.from(data)
        assertEquals(2, peers.size)

        val self = peers[0]
        assertTrue(self.isSelf)
        assertEquals("", self.name)
        assertEquals(1_760_000_000L, self.lastHandshakeEpoch)
        assertFalse(self.hasStoredConf)

        val guest = peers[1]
        assertFalse(guest.isSelf)
        assertEquals("Папа", guest.name)
        assertEquals("10.13.13.3", guest.ip)
        assertNull(guest.lastHandshakeEpoch) // hs=0 - ни разу не подключался
        assertTrue(guest.hasStoredConf)
    }

    @Test
    fun `broken name b64 falls back to empty`() {
        val data = ShareListData(peers = listOf(PeerDto(pub = "k=", nameB64 = "%%%")))
        assertEquals("", WgPeerParser.from(data).single().name)
    }

    @Test
    fun `empty data yields empty list`() {
        assertTrue(WgPeerParser.from(ShareListData()).isEmpty())
    }

    @Test
    fun `blank pub is skipped`() {
        val data = ShareListData(peers = listOf(PeerDto(pub = "")))
        assertTrue(WgPeerParser.from(data).isEmpty())
    }

    @Test
    fun `shared clients mapped with names`() {
        val data = ShareListData(
            clients = listOf(
                ClientDto(id = "0123456789abcdef0123456789abcdef", nameB64 = nameB64("Папа")),
                ClientDto(id = "fedcba9876543210fedcba9876543210"),
            ),
        )
        val clients = SharedClientParser.from(data)
        assertEquals(2, clients.size)
        assertEquals("0123456789abcdef0123456789abcdef", clients[0].clientId)
        assertEquals("Папа", clients[0].name)
        assertEquals("", clients[1].name) // без nameB64 -> пусто
    }

    @Test
    fun `shared client without id is skipped`() {
        val data = ShareListData(
            clients = listOf(
                ClientDto(id = ""),
                ClientDto(id = "fedcba9876543210fedcba9876543210"),
            ),
        )
        assertEquals(
            listOf("fedcba9876543210fedcba9876543210"),
            SharedClientParser.from(data).map { it.clientId }
        )
    }

    @Test
    fun `empty client list yields empty`() {
        assertTrue(SharedClientParser.from(ShareListData()).isEmpty())
    }
}
