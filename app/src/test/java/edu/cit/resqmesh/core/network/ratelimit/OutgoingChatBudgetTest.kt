package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingChatBudgetTest {
    private var now = 1_000_000L
    private val budget = OutgoingChatBudget(clock = { now })

    @Test fun startsFullWithNoWait() {
        assertEquals(ChatBudgetState(remaining = 20, capacity = 20, nextRefillInMs = 0L), budget.state.value)
    }

    @Test fun allowsTwentyThenBlocksAndReportsTheWait() {
        repeat(20) { assertTrue(budget.tryConsume()) }
        assertFalse(budget.tryConsume())
        assertEquals(ChatBudgetState(0, 20, 3_000L), budget.state.value)
    }

    @Test fun refillsOneTokenEveryThreeSeconds() {
        repeat(20) { budget.tryConsume() }
        now += 2_999L
        budget.refresh()
        assertEquals(ChatBudgetState(0, 20, 1L), budget.state.value)
        now += 1L
        assertTrue(budget.tryConsume())
        assertFalse(budget.tryConsume())
        now += 6_000L
        budget.refresh()
        assertEquals(2, budget.state.value.remaining)
    }

    @Test fun partialProgressShowsRemainingWaitAndStateIsPublished() {
        repeat(3) { budget.tryConsume() }
        assertEquals(ChatBudgetState(17, 20, 3_000L), budget.state.value)
        now += 1_000L
        budget.refresh()
        assertEquals(ChatBudgetState(17, 20, 2_000L), budget.state.value)
        now += 2_000L
        budget.refresh()
        assertEquals(ChatBudgetState(18, 20, 3_000L), budget.state.value)
    }

    @Test fun neverRefillsAboveCapacityAndFullMeansNoWait() {
        budget.tryConsume()
        now += 600_000L
        budget.refresh()
        assertEquals(ChatBudgetState(20, 20, 0L), budget.state.value)
    }

    @Test fun refundReturnsAToken() {
        repeat(20) { budget.tryConsume() }
        budget.refund()
        assertEquals(1, budget.state.value.remaining)
        assertTrue(budget.tryConsume())
    }
}
