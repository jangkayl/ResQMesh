package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/**
 * Counts violations per origin. [violationLimit] violations inside [windowMs] start a [cooldownMs]
 * cooldown. Tracked origins are bounded by [maxEntries] (LRU).
 */
class PenaltyBox(
    private val violationLimit: Int,
    private val windowMs: Long,
    private val cooldownMs: Long,
    private val clock: () -> Long,
    maxEntries: Int = 512
) {
    private class State(var count: Int, var windowStart: Long, var cooldownUntil: Long)

    private val states = BoundedLruMap<String, State>(maxEntries)

    fun isCoolingDown(origin: String): Boolean = synchronized(states) {
        val state = states[origin] ?: return false
        clock() < state.cooldownUntil
    }

    /** Records a violation; true when this one started a cooldown. */
    fun recordViolation(origin: String): Boolean = synchronized(states) {
        val now = clock()
        val state = states.getOrPut(origin) { State(0, now, 0L) }
        if (now < state.cooldownUntil) return false
        if (now - state.windowStart >= windowMs) {
            state.count = 0
            state.windowStart = now
        }
        state.count++
        if (state.count < violationLimit) return false
        state.count = 0
        state.cooldownUntil = now + cooldownMs
        true
    }

    fun forget(origin: String) {
        synchronized(states) { states.remove(origin) }
    }
}
