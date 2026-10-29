package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget
import org.junit.Assert.assertEquals
import org.junit.Test

class SosRaiseGateTest {
    private var now = 0L
    private var active = false
    private val budget = OutgoingSosBudget(clock = { now })
    private val gate = SosRaiseGate(budget) { active }

    @Test fun noTokenMeansNoRaiseAndTheMessageNamesTheWait() {
        repeat(3) { assertEquals(SosRaiseGate.Admission.NEW_RAISE, gate.admit()) }
        assertEquals(SosRaiseGate.Admission.NO_BUDGET, gate.admit())
        assertEquals("SOS available in 60 seconds", gate.waitMessage())
        now += 59_000L
        budget.refresh()
        assertEquals("SOS available in 1 second", gate.waitMessage())
    }

    @Test fun reopeningTheActiveSosIsNeverGatedOrCharged() {
        repeat(3) { gate.admit() }
        active = true
        assertEquals(SosRaiseGate.Admission.REOPEN, gate.admit())
        assertEquals(0, budget.state.value.remaining)
    }

    @Test fun aFailedCreateRefundsTheToken() {
        repeat(3) { gate.admit() }
        gate.refund()
        assertEquals(SosRaiseGate.Admission.NEW_RAISE, gate.admit())
    }
}
