package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosRaiseGateTest {
    private var now = 0L
    private val budget = OutgoingSosBudget(clock = { now })
    private val gate = SosRaiseGate(budget)

    @Test fun noTokenMeansRefusedAndTheMessageNamesTheWait() {
        repeat(3) { assertTrue(gate.tryCharge()) }
        assertFalse(gate.tryCharge())
        assertEquals("SOS available in 20 seconds", gate.waitMessage())
        now += 19_000L
        budget.refresh()
        assertEquals("SOS available in 1 second", gate.waitMessage())
    }

    @Test fun aFailedCreateRefundsTheToken() {
        repeat(3) { gate.tryCharge() }
        gate.refund()
        assertTrue(gate.tryCharge())
    }
}
