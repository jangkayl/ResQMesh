package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingSosBudgetTest {
    private var now = 1_000_000L
    private val budget = OutgoingSosBudget(clock = { now })

    @Test fun startsFullWithNoWait() {
        assertEquals(SosBudgetState(remaining = 3, capacity = 3, nextRefillInMs = 0L), budget.state.value)
    }

    @Test fun allowsThreeThenBlocksAndReportsTheWait() {
        repeat(3) { assertTrue(budget.tryConsume()) }
        assertFalse(budget.tryConsume())
        assertEquals(SosBudgetState(0, 3, 60_000L), budget.state.value)
    }

    @Test fun refillsOneRaiseEveryMinute() {
        repeat(3) { budget.tryConsume() }
        now += 59_999L
        budget.refresh()
        assertEquals(SosBudgetState(0, 3, 1L), budget.state.value)
        now += 1L
        assertTrue(budget.tryConsume())
        assertFalse(budget.tryConsume())
    }

    @Test fun refundRestoresAToken() {
        repeat(3) { budget.tryConsume() }
        budget.refund()
        assertEquals(1, budget.state.value.remaining)
        assertTrue(budget.tryConsume())
    }

    @Test fun neverExceedsCapacity() {
        budget.refund()
        now += 600_000L
        budget.refresh()
        assertEquals(SosBudgetState(3, 3, 0L), budget.state.value)
    }
}
