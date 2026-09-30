package com.example.testresqmesh.core.network

/** Acceptance by immediate neighbors, never a claim of recipient delivery. */
data class BroadcastDispatchResult(val neighbors: Map<String, TransportDispatchResult>) {
    val acceptedCount: Int get() = neighbors.values.count { it.accepted }
    val rejectedCount: Int get() = neighbors.size - acceptedCount
    val anyAccepted: Boolean get() = acceptedCount > 0

    fun feedback(): String? = when {
        neighbors.values.any { it == TransportDispatchResult.REJECTED_INVALID_FRAME } && !anyAccepted ->
            "Message is too large to send. Try a shorter recording or smaller image."
        !anyAccepted -> "Message waiting to send. It will retry when a connection has room."
        rejectedCount > 0 -> "Queued for $acceptedCount nearby connection(s); $rejectedCount could not accept it. Delivery is not confirmed."
        else -> null
    }
}
