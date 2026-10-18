package edu.cit.resqmesh.core.utils.collections

/** Thread-safe access-ordered map that evicts the least recently used entry beyond [maxEntries]. */
class BoundedLruMap<K : Any, V : Any>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val map = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > maxEntries
    }

    val size: Int get() = synchronized(map) { map.size }

    operator fun get(key: K): V? = synchronized(map) { map[key] }
    fun put(key: K, value: V): V? = synchronized(map) { map.put(key, value) }
    fun remove(key: K): V? = synchronized(map) { map.remove(key) }
    fun containsKey(key: K): Boolean = synchronized(map) { map.containsKey(key) }
    fun clear() = synchronized(map) { map.clear() }

    /** Snapshot of keys, least recently used first. */
    fun keys(): List<K> = synchronized(map) { map.keys.toList() }

    /** Returns the existing value (refreshing its recency) or stores and returns [create]'s result. */
    fun getOrPut(key: K, create: () -> V): V = synchronized(map) {
        map[key] ?: create().also { map[key] = it }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
