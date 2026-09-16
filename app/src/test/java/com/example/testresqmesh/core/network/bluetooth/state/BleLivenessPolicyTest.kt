package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleLivenessPolicyTest {
    @Test fun silenceExpiresOnlyAfterBoundedWindow() {
        val firstInbound = 1_000L
        assertFalse(BleLivenessPolicy.isStale(firstInbound, 21_000L))
        assertTrue(BleLivenessPolicy.isStale(firstInbound, 21_001L))
    }

    @Test fun recentInboundKeepsLinkAliveAfterLongProcessRuntime() {
        assertFalse(BleLivenessPolicy.isStale(31_000L, 45_000L))
        assertFalse(BleLivenessPolicy.isStale(0L, 45_000L))
    }

    @Test fun uiWarnsBeforeTransportIsRetired() {
        val lastInbound = 1_000L
        assertFalse(BleLivenessPolicy.isUnresponsive(lastInbound, 13_000L))
        assertTrue(BleLivenessPolicy.isUnresponsive(lastInbound, 13_001L))
        assertFalse(BleLivenessPolicy.isStale(lastInbound, 13_001L))
        assertTrue(BleLivenessPolicy.isStale(lastInbound, 21_001L))
    }
}
