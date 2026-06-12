package com.freeturn.app.data.share

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
    fun `parses peers with names handshake and conf flags`() {
        val kv = mapOf(
            "PEER_COUNT" to "2",
            "SELF_PUB" to "selfkey=",
            "PEER_0_PUB" to "selfkey=",
            "PEER_0_IP" to "10.13.13.2",
            "PEER_0_HS" to "1760000000",
            "PEER_0_CONF" to "no",
            "PEER_1_PUB" to "guestkey=",
            "PEER_1_NAME_B64" to nameB64("Папа"),
            "PEER_1_IP" to "10.13.13.3",
            "PEER_1_HS" to "0",
            "PEER_1_CONF" to "yes"
        )
        val peers = WgPeerParser.parse(kv)
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
        assertNull(guest.lastHandshakeEpoch) // HS=0 — ни разу не подключался
        assertTrue(guest.hasStoredConf)
    }

    @Test
    fun `broken name b64 falls back to empty`() {
        val kv = mapOf(
            "PEER_COUNT" to "1",
            "PEER_0_PUB" to "k=",
            "PEER_0_NAME_B64" to "%%%"
        )
        assertEquals("", WgPeerParser.parse(kv).single().name)
    }

    @Test
    fun `missing count or pub yields empty list`() {
        assertTrue(WgPeerParser.parse(emptyMap()).isEmpty())
        assertTrue(WgPeerParser.parse(mapOf("PEER_COUNT" to "1")).isEmpty())
    }

    @Test
    fun `shared clients parsed with names`() {
        val kv = mapOf(
            "CLIENT_COUNT" to "2",
            "CLIENT_0_ID" to "0123456789abcdef0123456789abcdef",
            "CLIENT_0_NAME_B64" to nameB64("Папа"),
            "CLIENT_1_ID" to "fedcba9876543210fedcba9876543210"
        )
        val clients = SharedClientParser.parse(kv)
        assertEquals(2, clients.size)
        assertEquals("0123456789abcdef0123456789abcdef", clients[0].clientId)
        assertEquals("Папа", clients[0].name)
        assertEquals("", clients[1].name) // без NAME_B64 → пусто
    }

    @Test
    fun `shared client without id is skipped`() {
        val kv = mapOf(
            "CLIENT_COUNT" to "2",
            "CLIENT_0_ID" to "",
            "CLIENT_1_ID" to "fedcba9876543210fedcba9876543210"
        )
        assertEquals(
            listOf("fedcba9876543210fedcba9876543210"),
            SharedClientParser.parse(kv).map { it.clientId }
        )
    }

    @Test
    fun `missing client count yields empty list`() {
        assertTrue(SharedClientParser.parse(emptyMap()).isEmpty())
    }
}
