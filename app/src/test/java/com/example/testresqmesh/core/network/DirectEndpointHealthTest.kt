package com.example.testresqmesh.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectEndpointHealthTest {
    @Test fun responsiveGattEndpointWinsOverAnUnresponsiveSocket() {
        val good = DirectEndpoint("GATT", "peer", false, true)
        val stale = DirectEndpoint("L2CAP", "peer", true, false)
        assertEquals(good, DirectSendPolicy.select(listOf(stale, good)))
        assertEquals(good, DirectSendPolicy.select(listOf(good, stale)))
    }
}
