package com.freeturn.app.data.share

import com.freeturn.app.data.control.ClientDto
import com.freeturn.app.data.control.ClientListData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedClientTest {

    @Test
    fun `peer fields only with pub`() {
        val list = SharedClient.list(
            ClientListData(
                clients = listOf(
                    ClientDto(name = "owner", clientId = "a", self = true, ip = "10.13.13.2", pub = "p=", hs = 1700000000),
                    ClientDto(name = "phone", clientId = "b")
                )
            )
        )
        assertEquals(2, list.size)
        assertTrue(list[0].isSelf)
        assertEquals("10.13.13.2", list[0].wgIp)
        assertEquals(1700000000L, list[0].lastHandshakeEpoch)
        assertNull(list[1].wgIp)
        assertNull(list[1].lastHandshakeEpoch)
    }

    @Test
    fun `zero handshake is never seen`() {
        val c = SharedClient.from(ClientDto(name = "x", clientId = "a", ip = "10.0.0.3", pub = "p=", hs = 0))
        assertNull(c.lastHandshakeEpoch)
    }

    @Test
    fun `nameless rows are dropped`() {
        assertTrue(SharedClient.list(ClientListData(clients = listOf(ClientDto(clientId = "a")))).isEmpty())
    }
}
