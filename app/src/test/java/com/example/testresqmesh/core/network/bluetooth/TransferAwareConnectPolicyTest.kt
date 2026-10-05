package com.example.testresqmesh.core.network.bluetooth

import org.junit.Assert.*
import org.junit.Test

class TransferAwareConnectPolicyTest {
    @Test fun optionalJoinWaitsForPayloadBoundary() {
        val policy = TransferAwareConnectPolicy()
        assertTrue(policy.defer("peer", true, true, 1000))
        assertFalse(policy.defer("peer", true, false, 2000))
    }
    @Test fun isolationRecoveryNeverWaitsForTransferDeferral() {
        assertFalse(TransferAwareConnectPolicy().defer("peer", false, true, 1000))
    }
    @Test fun continuousTrafficCannotStarveJoining() {
        val policy = TransferAwareConnectPolicy()
        assertTrue(policy.defer("peer", true, true, 1000))
        assertTrue(policy.defer("peer", true, true, 5999))
        assertFalse(policy.defer("peer", true, true, 6000))
    }
    @Test fun stoppedSessionDoesNotKeepOldDeferral() {
        val policy = TransferAwareConnectPolicy()
        policy.defer("peer", true, true, 1000); policy.clear()
        assertTrue(policy.defer("peer", true, true, 6000))
    }
}
