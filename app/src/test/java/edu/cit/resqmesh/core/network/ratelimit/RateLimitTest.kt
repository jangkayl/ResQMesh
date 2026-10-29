package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.network.ratelimit.OriginRateLimiter.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RateLimitTest {
    private var now = 0L
    private val clock: () -> Long = { now }

    @Test fun bucketAllowsBurstThenRefills() {
        val bucket = TokenBucket(3.0, 1.0 / 1000, clock)
        repeat(3) { assertTrue(bucket.tryConsume()) }
        assertFalse(bucket.tryConsume())
        now += 1000
        assertTrue(bucket.tryConsume())
        assertFalse(bucket.tryConsume())
        now += 1_000_000
        assertEquals(3.0, bucket.available(), 1e-9)
        assertFalse(bucket.tryConsume(3.5))
    }

    @Test fun penaltyBoxStartsAndEndsCooldown() {
        val box = PenaltyBox(3, 60_000, 60_000, clock)
        assertFalse(box.recordViolation("o"))
        assertFalse(box.recordViolation("o"))
        assertTrue(box.recordViolation("o"))
        assertTrue(box.isCoolingDown("o"))
        assertFalse(box.isCoolingDown("other"))
        now += 59_999
        assertTrue(box.isCoolingDown("o"))
        now += 1
        assertFalse(box.isCoolingDown("o"))
    }

    @Test fun penaltyViolationsOutsideWindowDoNotAccumulate() {
        val box = PenaltyBox(3, 1000, 5000, clock)
        box.recordViolation("o"); box.recordViolation("o")
        now += 1000
        assertFalse(box.recordViolation("o"))
        assertFalse(box.isCoolingDown("o"))
    }

    @Test fun limiterEnforcesBurstAndCoolsDownThenRecovers() {
        val limiter = OriginRateLimiter(RateLimitPolicy(), clock)
        repeat(25) { assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.CHAT)) }
        assertEquals(Decision.Dropped, limiter.check("o", TrafficClass.CHAT))
        assertEquals(Decision.Dropped, limiter.check("o", TrafficClass.CHAT))
        assertEquals(Decision.CooldownStarted, limiter.check("o", TrafficClass.CHAT))
        assertEquals(Decision.CoolingDown, limiter.check("o", TrafficClass.PRESENCE))
        now += 60_000
        assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.CHAT))
    }

    @Test fun limiterIsolatesOriginsAndClasses() {
        val limiter = OriginRateLimiter(RateLimitPolicy(), clock)
        assertEquals(Decision.Allowed, limiter.check("origin-A", TrafficClass.SOS_NEW))
        assertEquals(Decision.Dropped, limiter.check("origin-A", TrafficClass.SOS_NEW))
        assertEquals(Decision.Allowed, limiter.check("origin-B", TrafficClass.SOS_NEW))
        assertEquals(Decision.Allowed, limiter.check("origin-A", TrafficClass.CHAT))
        repeat(50) { assertEquals(Decision.Allowed, limiter.check("origin-A", TrafficClass.OTHER)) }
        now += 120_000
        assertEquals(Decision.Allowed, limiter.check("origin-A", TrafficClass.SOS_NEW))
    }

    @Test fun limiterBoundsTrackedOrigins() {
        val limiter = OriginRateLimiter(RateLimitPolicy(maxTrackedOrigins = 2), clock)
        limiter.check("a", TrafficClass.SOS_NEW)
        limiter.check("b", TrafficClass.SOS_NEW)
        limiter.check("c", TrafficClass.SOS_NEW) // evicts a
        assertEquals(Decision.Allowed, limiter.check("a", TrafficClass.SOS_NEW))
    }

    @Test fun shaperLimitsFramesPerSecondPerLink() {
        val shaper = LinkTrafficShaper(RateLimitPolicy(), clock)
        repeat(64) { assertTrue(shaper.admit("L1", 10)) }
        assertFalse(shaper.admit("L1", 10))
        assertTrue(shaper.admit("L2", 10))
        now += 1000
        assertTrue(shaper.admit("L1", 10))
    }

    @Test fun shaperLimitsBytesAndRejectionKeepsFrameBudget() {
        val shaper = LinkTrafficShaper(RateLimitPolicy(), clock)
        assertTrue(shaper.admit("L", 200 * 1024))
        assertFalse(shaper.admit("L", 100 * 1024))
        assertTrue(shaper.admit("L", 56 * 1024))
        now += 1000
        assertTrue(shaper.admit("L", 256 * 1024))
        shaper.forget("L")
        assertTrue(shaper.admit("L", 256 * 1024))
    }
}

class SosCooldownTest {
    private var now = 0L
    private val policy = RateLimitPolicy(chatBurst = 1.0, sosNewBurst = 1.0, sosUpdateBurst = 1.0)
    private val limiter = OriginRateLimiter(policy) { now }

    private fun coolDown(origin: String) {
        limiter.check(origin, TrafficClass.CHAT)
        repeat(2) { limiter.check(origin, TrafficClass.CHAT) }
        assertEquals(Decision.CooldownStarted, limiter.check(origin, TrafficClass.CHAT))
    }

    @Test fun cooldownFromOtherClassesNeverBlocksSos() {
        coolDown("o")
        assertEquals(Decision.CoolingDown, limiter.check("o", TrafficClass.PRESENCE))
        assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.SOS_NEW))
        assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.SOS_UPDATE))
        // SOS still obeys its own buckets while cooling down.
        assertEquals(Decision.Dropped, limiter.check("o", TrafficClass.SOS_NEW))
    }

    @Test fun sosViolationsCountTowardCooldownThatOnlyBlocksOtherClasses() {
        assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.SOS_NEW))
        assertEquals(Decision.Dropped, limiter.check("o", TrafficClass.SOS_NEW))
        assertEquals(Decision.Dropped, limiter.check("o", TrafficClass.SOS_NEW))
        assertEquals(Decision.CooldownStarted, limiter.check("o", TrafficClass.SOS_NEW))
        assertEquals(Decision.CoolingDown, limiter.check("o", TrafficClass.CHAT))
        now += 120_000
        assertEquals(Decision.Allowed, limiter.check("o", TrafficClass.SOS_NEW))
    }
}
