package edu.cit.resqmesh.feature.home.ui

import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind
import edu.cit.resqmesh.feature.home.ui.components.HomeRingTone
import edu.cit.resqmesh.feature.home.ui.components.homeRingTone
import edu.cit.resqmesh.feature.home.ui.components.homeStoryPeers
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStoryPeersTest {

    private fun node(name: String, kind: NodeKind, isBlocked: Boolean = false) = NodeItemData(
        endpointId = "",
        name = name,
        label = name,
        status = "",
        kind = kind,
        isBlocked = isBlocked
    )

    @Test
    fun offlinePeersStayVisibleAfterActivePeers() {
        val peers = homeStoryPeers(
            listOf(
                node("Ari", NodeKind.OFFLINE),
                node("Bea", NodeKind.DISCOVERED),
                node("Cai", NodeKind.DIRECT)
            )
        )

        assertEquals(listOf("Bea", "Cai", "Ari"), peers.map { it.name })
    }


    @Test
    fun blockedPeersShowLastIncludingOutOfRangeOnes() {
        val peers = homeStoryPeers(
            listOf(
                node("Ari", NodeKind.BLOCKED_OFFLINE, isBlocked = true),
                node("Bea", NodeKind.OFFLINE),
                node("Cai", NodeKind.DIRECT, isBlocked = true),
                node("Dee", NodeKind.DIRECT)
            )
        )

        assertEquals(listOf("Dee", "Bea", "Ari", "Cai"), peers.map { it.name })
        assertEquals(listOf(false, false, true, true), peers.map { it.isBlocked })
    }

    @Test
    fun onlyRoutedMultiHopPeersUseTheRelayTone() {
        val tones = NodeKind.entries.associateWith { homeRingTone(node("P", it)) }

        assertEquals(HomeRingTone.Direct, tones[NodeKind.DIRECT])
        assertEquals(HomeRingTone.Relayed, tones[NodeKind.RELAY])
        assertEquals(HomeRingTone.Relayed, tones[NodeKind.HOPPED])
        assertEquals(HomeRingTone.Checking, tones[NodeKind.UNRESPONSIVE])
        assertEquals(HomeRingTone.Checking, tones[NodeKind.HANDSHAKING])
        assertEquals(HomeRingTone.Checking, tones[NodeKind.SYNCING])
        assertEquals(HomeRingTone.Nearby, tones[NodeKind.DISCOVERED])
        assertEquals(HomeRingTone.Offline, tones[NodeKind.OFFLINE])
    }

    @Test
    fun relayedPeerThatDropsOffTheMeshLosesTheRelayTone() {
        // Route lost while still briefly in radio range -> discovered; out of range -> offline.
        assertEquals(HomeRingTone.Nearby, homeRingTone(node("Cai", NodeKind.DISCOVERED)))
        assertEquals(HomeRingTone.Offline, homeRingTone(node("Cai", NodeKind.OFFLINE)))
    }

    @Test
    fun blockedOverridesEveryLinkState() {
        NodeKind.entries.forEach { kind ->
            assertEquals(HomeRingTone.Blocked, homeRingTone(node("P", kind, isBlocked = true)))
        }
    }
}
