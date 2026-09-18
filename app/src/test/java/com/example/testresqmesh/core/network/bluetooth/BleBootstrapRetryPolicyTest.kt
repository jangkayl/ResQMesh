package com.example.testresqmesh.core.network.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleBootstrapRetryPolicyTest {
    @Test fun retryBackoffIsBoundedAndStartsPromptly() {
        assertEquals(1_000L, BleBootstrapRetryPolicy.retryDelay(1))
        assertEquals(2_000L, BleBootstrapRetryPolicy.retryDelay(2))
        assertEquals(4_000L, BleBootstrapRetryPolicy.retryDelay(3))
        assertEquals(8_000L, BleBootstrapRetryPolicy.retryDelay(4))
        assertEquals(15_000L, BleBootstrapRetryPolicy.retryDelay(5))
        assertEquals(15_000L, BleBootstrapRetryPolicy.retryDelay(20))
    }

    @Test fun oneStalledSetupCannotHoldBootstrapForTheOldWatchdogWindow() {
        assertTrue(BleBootstrapRetryPolicy.SETUP_PROGRESS_WINDOW_MS < 15_000L)
        assertEquals(750L, BleBootstrapRetryPolicy.BUSY_RETRY_MS)
    }
}
