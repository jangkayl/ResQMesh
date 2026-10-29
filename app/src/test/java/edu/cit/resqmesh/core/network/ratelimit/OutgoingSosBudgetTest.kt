package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingSosBudgetTest {
    private var now = 1_000_000L
    private val budget = OutgoingSosBudget(clock = { now })

    @Test fun startsFullWithNoWait() {
        assertEquals(SosBudgetState(remaining = 5, capacity = 5, nextRefillInMs = 0L), budget.state.value)
    }

    @Test fun allowsFiveThenBlocksAndReportsTheWait() {
        repeat(5) { assertTrue(budget.tryConsume()) }
        assertFalse(budget.tryConsume())
        assertEquals(SosBudgetState(0, 5, 12_000L), budget.state.value)
        assertEquals(12, budget.state.value.waitSeconds())
    }

    @Test fun refillsOneRaiseEveryTwelveSeconds() {
        repeat(5) { budget.tryConsume() }
        now += 11_999L
        budget.refresh()
        assertEquals(SosBudgetState(0, 5, 1L), budget.state.value)
        now += 1L
        assertTrue(budget.tryConsume())
        assertFalse(budget.tryConsume())
        now += 12_000L
        budget.refresh()
        assertEquals(SosBudgetState(1, 5, 12_000L), budget.state.value)
    }

    @Test fun refundRestoresAToken() {
        repeat(5) { budget.tryConsume() }
        budget.refund()
        assertEquals(1, budget.state.value.remaining)
        assertTrue(budget.tryConsume())
    }

    @Test fun neverExceedsCapacity() {
        budget.refund()
        now += 600_000L
        budget.refresh()
        assertEquals(SosBudgetState(5, 5, 0L), budget.state.value)
    }
}
