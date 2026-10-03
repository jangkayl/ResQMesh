package com.example.testresqmesh.core.network

/** Candidates have already passed identity, block and READY checks. */
internal data class DirectEndpoint(val endpoint: String, val owner: String, val l2cap: Boolean, val responsive: Boolean = true)

internal object DirectSendPolicy {
    const val SMALL_BYTES = 4 * 1024

    fun usesPieces(type: String, bytes: Int) = bytes > SMALL_BYTES && type in setOf("MESSAGE", "CONVERSATION")
    fun usesPeerEndpoint(type: String) = type in setOf("MESSAGE", "CONVERSATION", "SEEN", "DELIVERED")
    fun select(candidates: List<DirectEndpoint>): DirectEndpoint? = candidates.minWithOrNull(
        compareByDescending<DirectEndpoint> { it.responsive }.thenByDescending { it.l2cap }.thenBy { it.endpoint })
}
