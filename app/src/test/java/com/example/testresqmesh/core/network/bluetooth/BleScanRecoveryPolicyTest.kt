package com.example.testresqmesh.core.network.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleScanRecoveryPolicyTest {
    @Test fun retryBackoffIsBounded() {
        assertEquals(2_000L, BleScanRecoveryPolicy.retryDelay(1))
        assertEquals(5_000L, BleScanRecoveryPolicy.retryDelay(2))
        assertEquals(10_000L, BleScanRecoveryPolicy.retryDelay(3))
        assertEquals(30_000L, BleScanRecoveryPolicy.retryDelay(4))
        assertEquals(30_000L, BleScanRecoveryPolicy.retryDelay(20))
    }

    @Test fun staleWindowAvoidsRapidAndroidScanRestarts() {
        assertTrue(BleScanRecoveryPolicy.STALE_SCAN_AFTER_MS >= 15_000L)
        assertTrue(BleScanRecoveryPolicy.HEALTH_CHECK_MS < BleScanRecoveryPolicy.STALE_SCAN_AFTER_MS)
    }

    @Test fun recoveryRequiresActiveIsolatedNodeWithoutHandshake() {
        val stale = BleScanRecoveryPolicy.STALE_SCAN_AFTER_MS
        assertTrue(BleScanRecoveryPolicy.shouldRestart(true, false, false, true, stale))
        assertTrue(BleScanRecoveryPolicy.shouldRestart(true, false, false, false, 0L))
        assertEquals(false, BleScanRecoveryPolicy.shouldRestart(true, true, false, true, stale))
        assertEquals(false, BleScanRecoveryPolicy.shouldRestart(true, false, true, false, stale))
        assertEquals(false, BleScanRecoveryPolicy.shouldRestart(false, false, false, false, stale))
        assertEquals(false, BleScanRecoveryPolicy.shouldRestart(true, false, false, true, stale - 1L))
        assertEquals(false, BleScanRecoveryPolicy.shouldRestart(true, false, false, true, stale, stale + 2_000L))
    }
}
