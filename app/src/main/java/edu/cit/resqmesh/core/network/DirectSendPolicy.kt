package edu.cit.resqmesh.core.network

/** Candidates have already passed identity, block and READY checks. */
internal data class DirectEndpoint(val endpoint: String, val owner: String, val l2cap: Boolean, val responsive: Boolean = true)

/**
 * Ingress identity of a digest-verified transfer whose sender link has closed. The peer passed the
 * link's identity gate when its pieces arrived; this is never a Bluetooth address.
 */
internal object DetachedSender {
    private const val PREFIX = "transfer-peer:"
    fun endpointOf(peer: String) = PREFIX + peer
    fun peerOf(endpoint: String?): String? = endpoint?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
}

internal object DirectSendPolicy {
    const val SMALL_BYTES = 4 * 1024

    fun usesPieces(type: String, bytes: Int) = bytes > SMALL_BYTES && type in setOf("MESSAGE", "CONVERSATION")
    fun usesPeerEndpoint(type: String) = type in setOf("MESSAGE", "CONVERSATION", "SEEN", "DELIVERED")
    fun select(candidates: List<DirectEndpoint>): DirectEndpoint? = candidates.minWithOrNull(
        compareByDescending<DirectEndpoint> { it.responsive }.thenByDescending { it.l2cap }.thenBy { it.endpoint })
}
