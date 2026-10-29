package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget

/**
 * Admits one chat send: first the double-tap debounce, then a token from the shared outgoing budget.
 * A budget refusal sends nothing and leaves the draft alone; the composer already shows the wait.
 */
class ChatSendGate(val budget: OutgoingChatBudget, private val debouncer: SendDebouncer) {
    enum class Admission { GO, DEBOUNCED, NO_BUDGET }

    /** Charges one token when open. [markNow] false lets the caller mark the debounce only after a valid send. */
    fun admit(key: String, markNow: Boolean = true): Admission = when {
        debouncer.isBlocked(key) -> Admission.DEBOUNCED
        !budget.tryConsume() -> Admission.NO_BUDGET
        else -> {
            if (markNow) debouncer.mark(key)
            Admission.GO
        }
    }

    fun mark(key: String) = debouncer.mark(key)

    /** Returns the token of an admitted send that was refused before it left the device. */
    fun refund() = budget.refund()
}
