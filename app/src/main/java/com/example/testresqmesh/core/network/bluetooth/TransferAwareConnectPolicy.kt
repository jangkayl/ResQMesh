package com.example.testresqmesh.core.network.bluetooth

/** Optional joining waits for an idle payload boundary, but continuous traffic cannot starve it. */
class TransferAwareConnectPolicy {
    private val deferredAt = mutableMapOf<String, Long>()

    @Synchronized fun defer(peer: String, usableNeighbor: Boolean, busy: Boolean, now: Long): Boolean {
        if (!usableNeighbor || !busy) { deferredAt.remove(peer); return false }
        val first = deferredAt.getOrPut(peer) { now }
        if (now - first < MAX_DEFERRAL_MS) return true
        deferredAt.remove(peer)
        return false
    }

    @Synchronized fun clear() = deferredAt.clear()
    companion object { const val MAX_DEFERRAL_MS = 5_000L }
}
