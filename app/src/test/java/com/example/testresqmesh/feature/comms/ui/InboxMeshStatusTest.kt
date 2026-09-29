package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.feature.radar.ui.NodeItemData
import com.example.testresqmesh.feature.radar.ui.NodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxMeshStatusTest {
    @Test
    fun nearbyAndCheckingPeersDoNotCountAsReachable() {
        val status = inboxMeshStatus(
            active = true,
            nodes = listOf(node("Nearby", NodeKind.DISCOVERED), node("Checking", NodeKind.UNRESPONSIVE))
        )

        assertEquals(0, status.reachableCount)
        assertTrue(status.checking)
    }

    @Test
    fun reachableCountUsesOnlyUsableDirectAndRoutedPeers() {
        val status = inboxMeshStatus(
            active = true,
            nodes = listOf(
                node("Direct", NodeKind.DIRECT),
                node("Through mesh", NodeKind.HOPPED),
                node("Blocked", NodeKind.DIRECT, blocked = true),
                node("Blocked but routed", NodeKind.RELAY, blocked = true),
                node("Connecting", NodeKind.HANDSHAKING)
            )
        )

        assertEquals(3, status.reachableCount)
    }

    @Test
    fun permissionAndHardwareIssuesRemainVisibleDespiteCachedPeers() {
        val status = inboxMeshStatus(
            active = false,
            nodes = listOf(node("Cached", NodeKind.DIRECT)),
            permissionsReady = false,
            hardwareReady = false
        )

        assertFalse(status.permissionsReady)
        assertFalse(status.hardwareReady)
        assertFalse(status.active)
    }

    private fun node(name: String, kind: NodeKind, blocked: Boolean = false) =
        NodeItemData(endpointId = name, name = name, label = name, status = "", kind = kind, isBlocked = blocked)
}
