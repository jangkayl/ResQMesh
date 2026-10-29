package edu.cit.resqmesh.core.model

/** Outcome of asking to raise an SOS; decided by the repository under its lock. */
sealed interface SosRaiseResult {
    /** A new sosId was created (and charged). */
    data class Created(val id: String) : SosRaiseResult

    /** The phone already had an active own SOS; nothing was created or charged. */
    data class Reopened(val id: String) : SosRaiseResult

    /** A new raise was refused by the sender budget; nothing was created. */
    data object Throttled : SosRaiseResult
}

/** Sender budget as the repository sees it: charged only when a new sosId will really be created. */
interface SosRaiseCharge {
    fun tryCharge(): Boolean
    fun refund()

    companion object {
        val NONE = object : SosRaiseCharge {
            override fun tryCharge() = true
            override fun refund() = Unit
        }
    }
}
