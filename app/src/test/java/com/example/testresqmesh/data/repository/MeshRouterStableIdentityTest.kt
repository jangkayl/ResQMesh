package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRouterStableIdentityTest {
    @Test fun routeUsesStableIdsWhenPeerDisplayNameChanges() {
        val router = MeshRouter()
        val me = "Me#AAAA"
        val relay = "Relay renamed#BBBB"
        val target = "Target renamed#CCCC"

        router.updateTopology(
            senderName = relay,
            senderNodeId = "BBBB",
            connectedNodes = listOf(target),
            connectedNodeIds = listOf("CCCC"),
            myNodeName = me
        )

        assertEquals(
            listOf(me, "Old relay label#BBBB", target),
            router.findShortestPath(me, target, listOf(ConnectedDevice("endpoint", "Old relay label#BBBB", nodeId = "BBBB", isPayloadReady = true)))
        )
    }

    @Test fun legacyNameOnlyTopologyIsNotUsedForPrivateRoute() {
        val router = MeshRouter()
        router.updateTopology("Relay#BBBB", "BBBB", listOf("Target#CCCC"), emptyList(), "Me#AAAA")

        assertEquals(emptyList<String>(), router.findShortestPath(
            "Me#AAAA", "Target#CCCC", listOf(ConnectedDevice("endpoint", "Relay#BBBB", nodeId = "BBBB", isPayloadReady = true))
        ))
    }

    @Test fun emptyNewerTopologyWithdrawsPreviousStableRoute() {
        val router = MeshRouter()
        val me = "Me#AAAA"
        val relay = "Relay#BBBB"
        val target = "Target#CCCC"
        val directRelay = listOf(ConnectedDevice("endpoint", relay, nodeId = "BBBB", isPayloadReady = true))

        assertTrue(router.updateTopology(relay, "BBBB", listOf(target), listOf("CCCC"), me, topologySequence = 10L))
        assertEquals(listOf(me, relay, target), router.findShortestPath(me, target, directRelay))

        assertTrue(router.updateTopology(relay, "BBBB", emptyList(), emptyList(), me, topologySequence = 11L))
        assertEquals(emptyList<String>(), router.findShortestPath(me, target, directRelay))
    }

    @Test fun delayedOlderTopologyCannotRestoreWithdrawnRoute() {
        val router = MeshRouter()
        val me = "Me#AAAA"
        val relay = "Relay#BBBB"
        val target = "Target#CCCC"
        val directRelay = listOf(ConnectedDevice("endpoint", relay, nodeId = "BBBB", isPayloadReady = true))

        assertTrue(router.updateTopology(relay, "BBBB", emptyList(), emptyList(), me, topologySequence = 20L))
        assertFalse(router.updateTopology(relay, "BBBB", listOf(target), listOf("CCCC"), me, topologySequence = 19L))
        assertEquals(emptyList<String>(), router.findShortestPath(me, target, directRelay))
    }
}
