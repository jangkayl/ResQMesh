package com.example.testresqmesh.core.network.bluetooth

/** Conservative app-wide budget retained across radio restarts; uses a monotonic clock. */
class BleScanStartBudget {
    private val starts = ArrayDeque<Long>()
    fun delayUntilAllowed(now: Long): Long {
        while (starts.isNotEmpty() && now - starts.first() >= WINDOW_MS) starts.removeFirst()
        return if (starts.size < MAX_STARTS) 0L else (starts.first() + WINDOW_MS - now).coerceAtLeast(1L)
    }
    fun recordStart(now: Long) { starts.addLast(now) }
    companion object {
        const val WINDOW_MS = 30_000L
        const val MAX_STARTS = 4
    }
}
