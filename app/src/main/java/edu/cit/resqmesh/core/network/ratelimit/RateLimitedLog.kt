package edu.cit.resqmesh.core.network.ratelimit

import edu.cit.resqmesh.core.utils.collections.BoundedLruMap

/** Lets a marker through at most once per [intervalMs] per key so a flood cannot spam the terminal. */
class RateLimitedLog(
    private val intervalMs: Long,
    private val clock: () -> Long,
    maxKeys: Int = 256
) {
    private val lastLogged = BoundedLruMap<String, Long>(maxKeys)

    fun shouldLog(key: String): Boolean = synchronized(lastLogged) {
        val now = clock()
        val previous = lastLogged[key]
        if (previous != null && now - previous < intervalMs) return false
        lastLogged.put(key, now)
        true
    }
}
