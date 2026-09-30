package com.example.testresqmesh.core.network

import org.junit.Assert.*
import org.junit.Test

class BroadcastDispatchResultTest {
    @Test fun partialAcceptanceIsNotWhollyPendingOrReportedAsDelivered() {
        val result = BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED, "C" to TransportDispatchResult.REJECTED_QUEUE_FULL))
        assertTrue(result.anyAccepted)
        assertEquals(1, result.acceptedCount)
        assertEquals(1, result.rejectedCount)
        assertTrue(result.feedback()!!.contains("Delivery is not confirmed"))
    }
    @Test fun noAcceptedNeighborRemainsPendingAndInvalidFramesExplainRecovery() {
        assertFalse(BroadcastDispatchResult(emptyMap()).anyAccepted)
        assertTrue(BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.REJECTED_QUEUE_FULL)).feedback()!!.contains("retry"))
        assertTrue(BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.REJECTED_INVALID_FRAME)).feedback()!!.contains("shorter recording"))
        assertNull(BroadcastDispatchResult(mapOf("B" to TransportDispatchResult.ACCEPTED)).feedback())
    }
}
