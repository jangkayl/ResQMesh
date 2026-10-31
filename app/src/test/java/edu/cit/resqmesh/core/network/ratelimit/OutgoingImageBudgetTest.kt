package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingImageBudgetTest {
    private var now = 1_000_000L
    private val budget = OutgoingImageBudget(clock = { now })

    @Test fun startsFullWithNoWait() {
        assertEquals(ImageBudgetState(6, 6, 0L, 10_000L), budget.state.value)
    }

    @Test fun allowsSixStandardThenBlocks() {
        repeat(6) { assertTrue(budget.tryConsume(1)) }
        assertFalse(budget.tryConsume(1))
        assertEquals(10_000L, budget.state.value.nextRefillInMs)
    }

    @Test fun allowsThreeHighThenBlocks() {
        repeat(3) { assertTrue(budget.tryConsume(2)) }
        assertFalse(budget.tryConsume(2))
        assertEquals(20_000L, budget.state.value.waitMsFor(2))
    }

    @Test fun highNeedsTwoCreditsWhileStandardNeedsOne() {
        repeat(5) { budget.tryConsume(1) }
        assertFalse(budget.canAfford(2))
        assertTrue(budget.canAfford(1))
        assertEquals(10_000L, budget.state.value.waitMsFor(2))
    }

    @Test fun secondVeryHighWaitsTwentySeconds() {
        assertTrue(budget.tryConsume(4))
        assertFalse(budget.tryConsume(4))
        assertEquals(20_000L, budget.state.value.waitMsFor(4))
        now += 20_000L
        assertTrue(budget.tryConsume(4))
    }

    @Test fun refillsOneCreditEveryTenSeconds() {
        repeat(6) { budget.tryConsume(1) }
        now += 9_999L
        assertFalse(budget.canAfford(1))
        now += 1L
        assertTrue(budget.tryConsume(1))
    }

    @Test fun refundReturnsTheCharge() {
        repeat(3) { budget.tryConsume(2) }
        budget.refund(2)
        assertEquals(2, budget.state.value.remaining)
    }

    @Test fun noImageCostsNothing() {
        repeat(6) { budget.tryConsume(1) }
        assertTrue(budget.canAfford(0))
        assertTrue(budget.tryConsume(0))
        assertEquals(0, budget.state.value.remaining)
    }
}
