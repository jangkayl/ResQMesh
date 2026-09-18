package com.example.testresqmesh.core.network.bluetooth

/** Timing bounds for one serialized startup GATT lane. */
object BleBootstrapRetryPolicy {
    const val BUSY_RETRY_MS = 750L
    const val ACTIVE_SETUP_RECHECK_MS = 750L
    const val SETUP_PROGRESS_WINDOW_MS = 5_500L
    const val FIRST_FAILURE_RETRY_MS = 1_000L
    const val MAX_FAILURE_RETRY_MS = 15_000L

    fun retryDelay(attempts: Int): Long =
        (FIRST_FAILURE_RETRY_MS shl (attempts - 1).coerceAtMost(4)).coerceAtMost(MAX_FAILURE_RETRY_MS)
}
