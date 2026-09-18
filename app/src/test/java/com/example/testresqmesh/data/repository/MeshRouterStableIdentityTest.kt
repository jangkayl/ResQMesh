package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import org.junit.Assert.assertEquals
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
}
