package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptReplayLimiterTest {
    @Test
    fun repeatsInsideTheWindowAreRefusedPerOriginAndMessage() {
        var now = 0L
        val limiter = ReceiptReplayLimiter(intervalMs = 10_000, clock = { now })
        assertTrue(limiter.allow("a", "m1"))
        now = 9_999
        assertFalse(limiter.allow("a", "m1"))
        assertTrue(limiter.allow("a", "m2"))
        assertTrue(limiter.allow("b", "m1"))
        now = 10_000
        assertTrue(limiter.allow("a", "m1"))
    }

    @Test
    fun tableIsBounded() {
        val limiter = ReceiptReplayLimiter(intervalMs = 10_000, clock = { 0L }, maxKeys = 4)
        repeat(10) { limiter.allow("a", "m$it") }
        // m0 was evicted, so it is treated as new again.
        assertTrue(limiter.allow("a", "m0"))
        assertFalse(limiter.allow("a", "m9"))
    }
}
