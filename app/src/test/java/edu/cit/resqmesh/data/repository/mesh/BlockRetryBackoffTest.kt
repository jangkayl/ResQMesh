package edu.cit.resqmesh.data.repository.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRetryBackoffTest {
    @Test fun delayDoublesFromThreeSecondsAndCapsAtSixty() {
        val delays = (0..7).map(BlockRetryBackoff::delayMs)
        assertEquals(listOf(3_000L, 6_000L, 12_000L, 24_000L, 48_000L, 60_000L, 60_000L, 60_000L), delays)
        assertEquals(60_000L, BlockRetryBackoff.delayMs(Int.MAX_VALUE))
    }

    @Test fun deliverySlotIsOnlyTakenOnceRecorded() {
        val limiter = BlockDeliveryLimiter(30_000L)
        assertTrue(limiter.isOpen("C003", 1_000L))
        assertTrue(limiter.isOpen("C003", 1_001L))
        limiter.record("C003", 1_000L)
        assertFalse(limiter.isOpen("C003", 30_999L))
        assertTrue(limiter.isOpen("D004", 2_000L))
        assertTrue(limiter.isOpen("C003", 31_000L))
        limiter.clear("C003")
        assertTrue(limiter.isOpen("C003", 2_000L))
    }
}
