package edu.cit.resqmesh.feature.setup.viewmodel

/** In-memory backoff: after [maxAttempts] wrong PINs, reject everything for [lockMs]. Never stores the PIN. */
class PinAttemptLimiter(
    private val maxAttempts: Int = 5,
    private val lockMs: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var failures = 0
    private var lockedUntil = 0L

    @Synchronized
    fun remainingLockSeconds(): Int {
        val left = lockedUntil - clock()
        return if (left > 0) ((left + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt() else 0
    }

    @Synchronized
    fun recordFailure() {
        if (remainingLockSeconds() > 0) return
        failures++
        if (failures >= maxAttempts) {
            failures = 0
            lockedUntil = clock() + lockMs
        }
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        lockedUntil = 0L
    }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}
