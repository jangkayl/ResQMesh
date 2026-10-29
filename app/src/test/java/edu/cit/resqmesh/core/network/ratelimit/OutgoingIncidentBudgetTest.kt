package edu.cit.resqmesh.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingIncidentBudgetTest {
    private var now = 0L
    private val budget = OutgoingIncidentBudget { now }

    @Test fun threeReportsBackToBackThenOneEveryTwentySeconds() {
        repeat(3) { assertTrue(budget.tryConsumeReport()) }
        assertFalse(budget.tryConsumeReport())
        assertEquals(20, waitSeconds(budget.state.value.reportWaitMs))
        now += 19_000; budget.refresh()
        assertEquals(1, waitSeconds(budget.state.value.reportWaitMs))
        now += 1_000
        assertTrue(budget.tryConsumeReport())
    }

    @Test fun offersArePacedPerIncident() {
        repeat(5) { assertTrue(budget.tryConsumeOffer("a")) }
        assertFalse(budget.tryConsumeOffer("a"))
        assertTrue(budget.state.value.offerWaitMs("a") > 0)
        assertEquals(0L, budget.state.value.offerWaitMs("b"))
        assertTrue(budget.tryConsumeOffer("b"))
    }

    @Test fun refundReturnsATokenAfterAFailedSend() {
        repeat(3) { budget.tryConsumeReport() }
        budget.refundReport()
        assertEquals(0L, budget.state.value.reportWaitMs)
        assertTrue(budget.tryConsumeReport())
    }
}
