package com.example.testresqmesh.core.network.bluetooth.state

/** An ACK confirms the exact challenge sent on the current GATT generation. */
data class HeartbeatChallenge(
    val endpoint: String,
    val role: BleLinkRole,
    val generation: Long,
    val id: String,
    val createdAt: Long,
    val sentAt: Long = 0L
) {
    fun accepts(endpoint: String, id: String, currentGeneration: Long): Boolean =
        this.endpoint == endpoint && this.id == id && generation == currentGeneration

    fun expired(now: Long, timeoutMs: Long): Boolean = sentAt > 0L && now - sentAt >= timeoutMs
}
