package edu.cit.resqmesh.feature.radar.ui

import edu.cit.resqmesh.core.model.BlockRelationship
import edu.cit.resqmesh.core.model.BlockRelationshipOrigin
import edu.cit.resqmesh.core.model.BlockRelationshipStatus
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.KnownNode
import edu.cit.resqmesh.core.model.ScannedDevice
import edu.cit.resqmesh.core.ui.model.RadarUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind
import edu.cit.resqmesh.core.ui.peers.classifyRadarNodes
import edu.cit.resqmesh.feature.radar.ui.components.knownMeshPath

class RadarNodeClassificationTest {
    @Test fun unresponsiveDirectPeerRemainsVisibleAsChecking() {
        val nodes = classifyRadarNodes(
            RadarUiState(
                connectedDevices = listOf(
                    ConnectedDevice(
                        endpointId = "direct-endpoint",
                        name = "Peer#BBBB",
                        nodeId = "BBBB",
                        isPayloadReady = true,
                        isPeerResponsive = false
                    )
                )
            )
        )

        assertEquals(1, nodes.size)
        assertEquals(NodeKind.UNRESPONSIVE, nodes.single().kind)
    }

    @Test fun ordinaryPeerLabelOmitsIdAndLegacyDefaultTag() {
        val nodes = classifyRadarNodes(RadarUiState(
            connectedDevices = listOf(ConnectedDevice("ep", "Ari [NODE]#A1B2", nodeId = "A1B2", isPayloadReady = true))
        ))

        assertEquals("Ari", nodes.single().label)
        assertEquals("Ari [NODE]#A1B2", nodes.single().name)
    }

    @Test fun recentlyDisconnectedPeerRemainsVisibleAsOffline() {
        val peer = ConnectedDevice("old-endpoint", "Peer#BBBB", nodeId = "BBBB", isPayloadReady = true)

        val nodes = classifyRadarNodes(RadarUiState(recentOfflineDevices = listOf(peer)))

        assertEquals(NodeKind.OFFLINE, nodes.single().kind)
    }

    @Test fun nearbyRelayedPeerKeepsEndpointForDirectConnectAction() {
        val peer = "Peer#BBBB"
        val route = listOf("Me#AAAA", "Relay#CCCC", peer)
        val nodes = classifyRadarNodes(
            RadarUiState(
                knownNodes = listOf(KnownNode(peer, isDirect = false, lastSeen = 1L, route = route)),
                scannedDevices = listOf(ScannedDevice("scan-endpoint", peer, lastSeen = 1L, nodeId = "BBBB"))
            )
        )

        assertTrue(nodes.single().kind == NodeKind.RELAY)
        assertEquals("scan-endpoint", nodes.single().endpointId)
        assertEquals(route, nodes.single().route)
    }

    @Test fun peerDetailsUseRepositoryVerifiedDirectedPath() {
        val target = NodeItemData(
            endpointId = "",
            name = "Target#CCCC",
            status = "Reachable via mesh",
            kind = NodeKind.HOPPED,
            label = "Target #CCCC",
            route = listOf("Me#AAAA", "Relay#BBBB", "Target#CCCC")
        )

        assertEquals(listOf("You", "Relay", "Target"), knownMeshPath(target))
    }

    @Test fun stableIdentityAppearsOnlyOnceAcrossDirectScanAndRouteInputs() {
        val direct = ConnectedDevice("direct-endpoint", "Complete Peer#BBBB", nodeId = "BBBB", isPayloadReady = true)
        val nodes = classifyRadarNodes(
            RadarUiState(
                connectedDevices = listOf(direct),
                scannedDevices = listOf(ScannedDevice("scan-endpoint", "Complete Pe#BBBB", 1L, nodeId = "BBBB")),
                knownNodes = listOf(KnownNode("Old Peer#BBBB", isDirect = false, lastSeen = 1L))
            )
        )

        assertEquals(1, nodes.size)
        assertEquals(NodeKind.DIRECT, nodes.single().kind)
    }

    @Test fun unblockingDoesNotPromoteRelayedPeerToDirect() {
        val peer = "Peer#BBBB"
        val routed = KnownNode(peer, isDirect = false, lastSeen = 1L, route = listOf("Me#AAAA", "Relay#CCCC", peer))

        val blocked = classifyRadarNodes(
            RadarUiState(knownNodes = listOf(routed), blockedDeviceNames = setOf(peer))
        ).single()
        val unblocked = classifyRadarNodes(RadarUiState(knownNodes = listOf(routed))).single()

        assertTrue(blocked.isBlocked)
        assertEquals(NodeKind.HOPPED, blocked.kind)
        assertTrue(!unblocked.isBlocked)
        assertEquals(NodeKind.HOPPED, unblocked.kind)
    }

    @Test fun blockedHandshakingLinkStaysBlockedByNodeId() {
        val nodes = classifyRadarNodes(RadarUiState(
            connectedDevices = listOf(
                ConnectedDevice("ep-12345", "Unknown Node", nodeId = "BBBB", isPayloadReady = false)
            ),
            blockedDeviceNames = setOf("Bea [NODE]#BBBB")
        ))

        val row = nodes.single()
        assertEquals(NodeKind.HANDSHAKING, row.kind)
        assertTrue(row.isBlocked)
    }

    @Test fun unblockedHandshakingLinkIsNotBlocked() {
        val nodes = classifyRadarNodes(RadarUiState(
            connectedDevices = listOf(
                ConnectedDevice("ep-12345", "Unknown Node", nodeId = "CCCC", isPayloadReady = false)
            ),
            blockedDeviceNames = setOf("Bea [NODE]#BBBB")
        ))

        assertTrue(nodes.first { it.kind == NodeKind.HANDSHAKING }.isBlocked.not())
    }

    @Test fun blockedScannedPeerIsDiscoveredAndBlockedNotOffline() {
        val nodes = classifyRadarNodes(RadarUiState(
            scannedDevices = listOf(ScannedDevice("aa:bb", "Bea [NODE]#BBBB", 0L)),
            blockedDeviceNames = setOf("Bea [NODE]#BBBB")
        ))

        val row = nodes.single()
        assertEquals(NodeKind.DISCOVERED, row.kind)
        assertTrue(row.isBlocked)
    }
    @Test fun relationshipOriginAndTombstoneReachTheRows() {
        val theyBlocked = BlockRelationship(
            "Bea [NODE]#BBBB", "op1", BlockRelationshipOrigin.REMOTE, BlockRelationshipStatus.CONFIRMED, 0L
        )
        val released = BlockRelationship(
            "Cai [NODE]#CCCC", "op2", BlockRelationshipOrigin.LOCAL, BlockRelationshipStatus.RELEASED_LOCALLY, 0L
        )
        val nodes = classifyRadarNodes(RadarUiState(
            scannedDevices = listOf(
                ScannedDevice("aa:bb", "Bea [NODE]#BBBB", 0L),
                ScannedDevice("cc:dd", "Cai [NODE]#CCCC", 0L)
            ),
            blockedDeviceNames = setOf("Bea [NODE]#BBBB"),
            blockRelationships = mapOf("BBBB" to theyBlocked, "CCCC" to released)
        ))

        val bea = nodes.first { it.name.startsWith("Bea") }
        val cai = nodes.first { it.name.startsWith("Cai") }
        assertTrue(bea.isBlocked && bea.blockedByThem && !bea.releasedLocally)
        assertTrue(!cai.isBlocked && !cai.blockedByThem && cai.releasedLocally)
    }
}
