package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedPeerRowsTest {
    private val peer = "Alice#AB12"
    private val kept = ConnectedDevice("E1", peer, isClassicConnected = true, nodeId = "AB12", isPayloadReady = true)
    private val placeholder = ConnectedDevice(
        "E2", NodeIdentity.UNKNOWN_NAME, isClassicConnected = true, isProvisional = true, isPayloadReady = true
    )
    private val other = ConnectedDevice("E3", "Bob#CD34", nodeId = "CD34", isPayloadReady = true)
    private val renamed = placeholder.copy(name = peer, isProvisional = false, nodeId = "AB12")

    @Test
    fun `merging a renamed duplicate removes its placeholder row`() {
        val rows = ConnectedPeerRows.mergeIntoKept(listOf(kept, placeholder, other), "E1", renamed, keptIsReady = true)

        assertEquals(listOf("E1", "E3"), rows.map { it.endpointId })
        assertFalse(rows.any { NodeIdentity.isPlaceholder(it.name) })
    }

    @Test
    fun `merging a new duplicate without a row leaves other rows alone`() {
        val rows = ConnectedPeerRows.mergeIntoKept(listOf(kept, other), "E1", renamed, keptIsReady = true)

        assertEquals(listOf(kept, other), rows)
    }

    @Test
    fun `kept row adopts the resolved identity`() {
        val unnamed = kept.copy(name = NodeIdentity.UNKNOWN_NAME, isProvisional = true, nodeId = "")

        val row = ConnectedPeerRows.mergeIntoKept(listOf(unnamed), "E1", renamed, keptIsReady = false).single()

        assertEquals(peer, row.name)
        assertEquals("AB12", row.nodeId)
        assertFalse(row.isProvisional)
        assertFalse(row.isPayloadReady)
    }

    @Test
    fun `disconnect repoints the row when the peer survives on another socket`() {
        val row = ConnectedPeerRows.afterDisconnect(
            listOf(kept.copy(isPeerResponsive = false)), "E1", keepRow = false, survivorEndpoint = "E2"
        ).single()

        assertEquals("E2", row.endpointId)
        assertEquals(peer, row.name)
        assertTrue(row.isPayloadReady)
        assertTrue(row.isPeerResponsive)
    }

    @Test
    fun `disconnect removes the row when no ready socket remains`() {
        val rows = ConnectedPeerRows.afterDisconnect(
            listOf(kept, other), "E1", keepRow = false, survivorEndpoint = null
        )

        assertEquals(listOf(other), rows)
    }

    @Test
    fun `disconnect keeps the row while its socket is still live`() {
        val rows = ConnectedPeerRows.afterDisconnect(listOf(kept), "E1", keepRow = true, survivorEndpoint = null)

        assertEquals(listOf(kept), rows)
    }

    @Test
    fun `disconnect never creates a second row for the survivor`() {
        val survivor = kept.copy(endpointId = "E2")

        val rows = ConnectedPeerRows.afterDisconnect(
            listOf(kept, survivor), "E1", keepRow = false, survivorEndpoint = "E2"
        )

        assertEquals(listOf(survivor), rows)
    }

    @Test
    fun `disconnect ignores a survivor equal to the lost endpoint`() {
        val rows = ConnectedPeerRows.afterDisconnect(listOf(kept), "E1", keepRow = false, survivorEndpoint = "E1")

        assertTrue(rows.isEmpty())
    }
}
