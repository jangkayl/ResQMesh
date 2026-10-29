package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget

/**
 * Admits a call to raise an SOS. Only a NEW raise (no own active SOS) takes a token; reopening the active SOS
 * is never gated, and ending or refining never reaches this gate at all.
 */
class SosRaiseGate(val budget: OutgoingSosBudget, private val hasActiveOwnSos: () -> Boolean) {
    enum class Admission { REOPEN, NEW_RAISE, NO_BUDGET }

    fun admit(): Admission = when {
        hasActiveOwnSos() -> Admission.REOPEN
        !budget.tryConsume() -> Admission.NO_BUDGET
        else -> Admission.NEW_RAISE
    }

    /** Returns the token of an admitted new raise whose create failed. */
    fun refund() = budget.refund()

    /** Feedback for a refused raise, such as "SOS available in 42 seconds". */
    fun waitMessage(): String {
        val seconds = ((budget.state.value.nextRefillInMs + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtLeast(1L)
        return "SOS available in $seconds ${if (seconds == 1L) "second" else "seconds"}"
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}
