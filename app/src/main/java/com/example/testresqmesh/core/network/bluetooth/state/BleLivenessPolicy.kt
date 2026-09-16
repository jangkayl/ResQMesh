package com.example.testresqmesh.core.network.bluetooth.state

/** A live peer must deliver app traffic or a heartbeat within the bounded silence window. */
object BleLivenessPolicy {
    const val MAX_INBOUND_SILENCE_MS = 20_000L
    const val UNRESPONSIVE_AFTER_MS = 12_000L

    fun isUnresponsive(lastInboundAt: Long, now: Long): Boolean =
        lastInboundAt > 0L && now - lastInboundAt > UNRESPONSIVE_AFTER_MS

    fun isStale(lastInboundAt: Long, now: Long): Boolean =
        lastInboundAt > 0L && now - lastInboundAt > MAX_INBOUND_SILENCE_MS
}
