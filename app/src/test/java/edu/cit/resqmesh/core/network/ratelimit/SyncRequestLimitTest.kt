package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.network.MeshPayload
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sync requests are never rate limited, but only a direct request from the link's own peer is served. */
class SyncRequestLimitTest {
    private val gate = InboundOriginGate(OriginRateLimiter(RateLimitPolicy()) { 0L }, { 0L })
    private var next = 0

    private fun request(type: String = "EVENT_SYNC_REQ", sender: String = "A001", admitted: String? = "A001",
        hops: Int = 0, route: List<String> = emptyList()) = gate.admit("ep", MeshPayload(
        id = "req-${next++}", type = type, senderName = "Peer#$sender", senderNodeId = sender, relayHopCount = hops,
        routePath = route, text = JSONObject().put("protocol", 3).put("op", "DIGEST").toString()
    ), myNodeId = "B001", admittedNodeId = admitted)

    @Test fun anyBurstFromTheAdmittedPeerIsServed() {
        repeat(1_000) { assertTrue(request()) }
        repeat(1_000) { assertTrue(request(type = "SOS_SYNC_REQ")) }
    }

    @Test fun requestBeforeAdmissionIsServedForTheCoordinatorToCheck() {
        assertTrue(request(admitted = null))
    }

    @Test fun relayedSyncRequestsAreRejected() {
        assertFalse(request(sender = "C001", hops = 1, route = listOf("Far#C001", "Peer#A001")))
    }

    @Test fun originMismatchAndSelfOriginClaimsAreRejected() {
        assertFalse(request(sender = "C001"))
        assertFalse(request(sender = "B001"))
    }
}
