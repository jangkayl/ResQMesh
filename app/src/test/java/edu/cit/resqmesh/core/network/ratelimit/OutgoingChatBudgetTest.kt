package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingChatBudgetTest {
    private var now = 1_000_000L
    private val budget = OutgoingChatBudget(clock = { now })

    @Test fun startsFullWithNoWait() {
        assertEquals(ChatBudgetState(remaining = 15, capacity = 15, nextRefillInMs = 0L), budget.state.value)
    }

    @Test fun allowsFifteenThenBlocksAndReportsTheWait() {
        repeat(15) { assertTrue(budget.tryConsume()) }
        assertFalse(budget.tryConsume())
        assertEquals(ChatBudgetState(0, 15, 4_000L), budget.state.value)
    }

    @Test fun refillsOneTokenEveryFourSeconds() {
        repeat(15) { budget.tryConsume() }
        now += 3_999L
        budget.refresh()
        assertEquals(ChatBudgetState(0, 15, 1L), budget.state.value)
        now += 1L
        assertTrue(budget.tryConsume())
        assertFalse(budget.tryConsume())
        now += 8_000L
        budget.refresh()
        assertEquals(2, budget.state.value.remaining)
    }

    @Test fun partialProgressShowsRemainingWaitAndStateIsPublished() {
        repeat(3) { budget.tryConsume() }
        assertEquals(ChatBudgetState(12, 15, 4_000L), budget.state.value)
        now += 1_000L
        budget.refresh()
        assertEquals(ChatBudgetState(12, 15, 3_000L), budget.state.value)
        now += 3_000L
        budget.refresh()
        assertEquals(ChatBudgetState(13, 15, 4_000L), budget.state.value)
    }

    @Test fun neverRefillsAboveCapacityAndFullMeansNoWait() {
        budget.tryConsume()
        now += 600_000L
        budget.refresh()
        assertEquals(ChatBudgetState(15, 15, 0L), budget.state.value)
    }

    @Test fun refundReturnsAToken() {
        repeat(15) { budget.tryConsume() }
        budget.refund()
        assertEquals(1, budget.state.value.remaining)
        assertTrue(budget.tryConsume())
    }
}
