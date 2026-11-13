package edu.cit.resqmesh.core.network.bluetooth.state

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectTimeoutPolicyTest {
    @Test fun unknownPeerStartsAtBase() {
        assertEquals(ConnectTimeoutPolicy.BASE_MS, ConnectTimeoutPolicy().timeoutMs("peer"))
    }

    @Test fun consecutiveTimeoutsEscalateAndCap() {
        val policy = ConnectTimeoutPolicy()
        policy.onTimedOut("peer")
        assertEquals(ConnectTimeoutPolicy.BASE_MS + ConnectTimeoutPolicy.STEP_MS, policy.timeoutMs("peer"))
        policy.onTimedOut("peer")
        assertEquals(ConnectTimeoutPolicy.MAX_MS, policy.timeoutMs("peer"))
        repeat(5) { policy.onTimedOut("peer") }
        assertEquals(ConnectTimeoutPolicy.MAX_MS, policy.timeoutMs("peer"))
    }

    @Test fun connectResetsOnlyThatPeer() {
        val policy = ConnectTimeoutPolicy()
        policy.onTimedOut("slow")
        policy.onTimedOut("other")
        policy.onConnected("slow")
        assertEquals(ConnectTimeoutPolicy.BASE_MS, policy.timeoutMs("slow"))
        assertEquals(ConnectTimeoutPolicy.BASE_MS + ConnectTimeoutPolicy.STEP_MS, policy.timeoutMs("other"))
    }

    @Test fun capStaysBelowConnectLockBackstop() {
        assert(ConnectTimeoutPolicy.MAX_MS < 25_000L)
    }

    @Test fun boundedPeerTableEvictsOldest() {
        val policy = ConnectTimeoutPolicy(maxPeers = 2)
        policy.onTimedOut("a")
        policy.onTimedOut("b")
        policy.onTimedOut("c")
        assertEquals(ConnectTimeoutPolicy.BASE_MS, policy.timeoutMs("a"))
        assertEquals(ConnectTimeoutPolicy.BASE_MS + ConnectTimeoutPolicy.STEP_MS, policy.timeoutMs("c"))
    }
}
