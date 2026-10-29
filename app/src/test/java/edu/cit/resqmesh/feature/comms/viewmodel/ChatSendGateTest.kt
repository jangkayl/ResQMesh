package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import edu.cit.resqmesh.feature.comms.viewmodel.ChatSendGate.Admission
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSendGateTest {
    private var now = 0L
    private val budget = OutgoingChatBudget(clock = { now })
    private val gate = ChatSendGate(budget, SendDebouncer(400L) { now })

    @Test fun noTokenMeansNoSendAndTheBudgetStaysEmpty() {
        while (budget.tryConsume()) Unit
        assertEquals(Admission.NO_BUDGET, gate.admit("public:txt"))
        assertEquals(0, budget.state.value.remaining)
    }

    @Test fun doubleTapInsideTheDebounceWindowIsNotChargedTwice() {
        assertEquals(Admission.GO, gate.admit("public:txt"))
        assertEquals(Admission.DEBOUNCED, gate.admit("public:txt"))
        assertEquals(19, budget.state.value.remaining)
    }

    @Test fun refusedBudgetDoesNotMarkTheDebounceSoTheNextTapAfterRefillSends() {
        while (budget.tryConsume()) Unit
        assertEquals(Admission.NO_BUDGET, gate.admit("private:Bob:txt", markNow = false))
        now += 3_000L
        assertEquals(Admission.GO, gate.admit("private:Bob:txt"))
    }

    @Test fun refundRestoresTheTokenOfAnAdmittedButRefusedSend() {
        gate.admit("public:txt")
        gate.refund()
        assertEquals(20, budget.state.value.remaining)
    }
}
