package edu.cit.resqmesh.core.network.bluetooth.state

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Per-peer GATT connect deadline. Most peers connect well inside [BASE_MS]; a slow peer (observed on a
 * vivo V2424) was cancelled at that deadline on every attempt, so each consecutive timeout extends that
 * peer's next deadline by [STEP_MS] up to [MAX_MS]. A completed connect resets it. Keyed by stable peer
 * identity because private addresses rotate between attempts.
 */
class ConnectTimeoutPolicy(maxPeers: Int = MAX_PEERS) {
    private val consecutiveTimeouts = BoundedLruMap<String, Int>(maxPeers)

    fun timeoutMs(peerKey: String): Long =
        (BASE_MS + STEP_MS * (consecutiveTimeouts[peerKey] ?: 0)).coerceAtMost(MAX_MS)

    fun onTimedOut(peerKey: String) {
        val next = ((consecutiveTimeouts[peerKey] ?: 0) + 1).coerceAtMost(MAX_STEPS)
        consecutiveTimeouts[peerKey] = next
    }

    fun onConnected(peerKey: String) {
        consecutiveTimeouts.remove(peerKey)
    }

    fun clear() = consecutiveTimeouts.clear()

    companion object {
        const val BASE_MS = 8_000L
        const val STEP_MS = 5_000L
        /** Stays below the 25 s connect-lock backstop so one slow peer cannot strand the lane. */
        const val MAX_MS = 15_000L
        /** Rounded up so the last step still reaches [MAX_MS] when the range is not a multiple of [STEP_MS]. */
        private const val MAX_STEPS = ((MAX_MS - BASE_MS + STEP_MS - 1) / STEP_MS).toInt()
        private const val MAX_PEERS = 256
    }
}
