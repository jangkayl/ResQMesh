package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.model.SosRaiseCharge
import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget

/**
 * The sender budget as the repository's charge hook. The repository calls [tryCharge] under its lock only when
 * it will really create a new sosId, so a reopen of the active SOS never spends a token and a genuine new raise
 * is never skipped. Ending and refining never reach it.
 */
class SosRaiseGate(val budget: OutgoingSosBudget) : SosRaiseCharge {
    override fun tryCharge(): Boolean = budget.tryConsume()

    /** Returns the token of a new raise whose creation failed. */
    override fun refund() = budget.refund()

    /** Feedback for a refused raise, such as "SOS available in 42 seconds". */
    fun waitMessage(): String {
        val seconds = ((budget.state.value.nextRefillInMs + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtLeast(1L)
        return "SOS available in $seconds ${if (seconds == 1L) "second" else "seconds"}"
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}
