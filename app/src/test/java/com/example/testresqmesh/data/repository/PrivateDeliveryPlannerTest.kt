package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateDeliveryPlannerTest {
    @Test fun directRecipientWinsOverAForwardingRoute() {
        val devices = listOf(device("recipient", "direct"), device("relay", "relay-endpoint"))

        assertEquals(
            PrivateDeliveryPlanner.Target.Endpoint("direct"),
            PrivateDeliveryPlanner.select("recipient", listOf("me", "relay", "recipient"), devices)
        )
    }

    @Test fun routeUsesItsReadyNextHop() {
        assertEquals(
            PrivateDeliveryPlanner.Target.Endpoint("relay-endpoint"),
            PrivateDeliveryPlanner.select(
                "recipient",
                listOf("me", "relay", "recipient"),
                listOf(device("relay", "relay-endpoint"))
            )
        )
    }

    @Test fun unavailableNextHopFallsBackToMeshBroadcast() {
        assertEquals(
            PrivateDeliveryPlanner.Target.Broadcast,
            PrivateDeliveryPlanner.select("recipient", listOf("me", "missing", "recipient"), emptyList())
        )
    }

    private fun device(name: String, endpointId: String) = ConnectedDevice(
        endpointId = endpointId,
        name = name,
        isClassicConnected = true,
        isPayloadReady = true
    )
}
