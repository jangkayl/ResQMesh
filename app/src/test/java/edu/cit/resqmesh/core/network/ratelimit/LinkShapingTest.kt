package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkShapingTest {
    private var now = 0L
    private val shaper = LinkTrafficShaper(RateLimitPolicy(), { now })

    @Test fun gattChunksUseOnlyTheByteBudgetAndNeverTripTheFrameRate() {
        repeat(500) { assertTrue(shaper.admitChunk("ep/client", 512)) } // 500 chunks in the same ms, 250 KiB
    }

    @Test fun chunkOverBudgetIsDroppedUntilDebtIsRepaid() {
        assertTrue(shaper.admitChunk("ep/server", 262_144)) // budget exactly consumed
        assertFalse(shaper.admitChunk("ep/server", 100))
        now += 1000
        assertTrue(shaper.admitChunk("ep/server", 100))
    }

    @Test fun oneChunkLargerThanTheBudgetIsAdmittedWithDebt() {
        assertTrue(shaper.admitChunk("ep/server", 600_000))
        assertFalse(shaper.admitChunk("ep/server", 1))
        assertTrue(shaper.admitChunk("other/server", 1))
    }

    @Test fun twoMiBL2capFrameIsDelayedNeverRefused() {
        var waited = 0L
        repeat(128) { // 2 MiB in 16 KiB reads
            val delay = shaper.chargeStream("ep/l2cap", 16 * 1024)
            waited += delay
            now += delay
        }
        // 256 KiB free, remaining 1.75 MiB at 256 KiB/s is about 7 s of pacing in total.
        assertTrue("waited=$waited", waited in 6_500L..7_500L)
    }

    @Test fun guardsLogOnlyThroughRateLimitedMarkerAndStayBounded() {
        val guards = InboundGuards(RateLimitPolicy(), { now })
        assertTrue(guards.admitChunk("ep/client", 262_144))
        repeat(1000) { assertFalse(guards.admitChunk("ep/client", 10)) }
        assertEquals(0L, guards.streamDelayMs("fresh/l2cap", 1))
    }
}
