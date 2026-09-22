package com.example.testresqmesh.core.network.bluetooth

/** Timing bounds for recovering discovery when an active node has no usable direct neighbor. */
object BleScanRecoveryPolicy {
    const val STALE_SCAN_AFTER_MS = 15_000L
    const val HEALTH_CHECK_MS = 5_000L
    const val MAX_RETRY_DELAY_MS = 30_000L
    const val RETRY_JITTER_MS = 2_000L

    fun retryDelay(attempts: Int): Long = when (attempts.coerceAtLeast(1)) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 10_000L
        else -> MAX_RETRY_DELAY_MS
    }

    fun shouldRestart(
        nodeActive: Boolean,
        hasReadyConnection: Boolean,
        handshakeActive: Boolean,
        scanActive: Boolean,
        millisSinceScanProgress: Long,
        staleAfterMs: Long = STALE_SCAN_AFTER_MS
    ): Boolean = nodeActive &&
        !hasReadyConnection &&
        !handshakeActive &&
        (!scanActive || millisSinceScanProgress >= staleAfterMs)
}
