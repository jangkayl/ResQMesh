package edu.cit.resqmesh.core.network.dedup

/**
 * Seen-ID cache with O(1) lookup, a [ttlMs] lifetime and a [maxEntries] cap (oldest insert evicted first).
 * Re-marking an ID does not extend its lifetime, so a replaying peer cannot pin entries.
 */
class ExpiringIdCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(maxEntries > 0 && ttlMs > 0) { "maxEntries and ttlMs must be positive" }
    }

    private val seenAt = LinkedHashMap<String, Long>() // insertion order == expiry order

    /** True when [id] was not already present (and is now recorded). */
    fun markSeen(id: String): Boolean = synchronized(seenAt) {
        val now = clock()
        evictExpired(now)
        if (seenAt.containsKey(id)) return false
        seenAt[id] = now
        while (seenAt.size > maxEntries) seenAt.remove(seenAt.keys.first())
        true
    }

    fun contains(id: String): Boolean = synchronized(seenAt) {
        evictExpired(clock())
        seenAt.containsKey(id)
    }

    val size: Int get() = synchronized(seenAt) { evictExpired(clock()); seenAt.size }

    private fun evictExpired(now: Long) {
        val iterator = seenAt.entries.iterator()
        while (iterator.hasNext() && now - iterator.next().value >= ttlMs) iterator.remove()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 4096
        const val DEFAULT_TTL_MS = 24L * 60 * 60 * 1000
    }
}
