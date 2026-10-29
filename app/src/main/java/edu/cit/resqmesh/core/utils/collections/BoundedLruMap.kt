package edu.cit.resqmesh.core.utils.collections

/** Thread-safe access-ordered map that evicts the least recently used entry beyond [maxEntries]. */
@Suppress("TooManyFunctions")
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
    operator fun set(key: K, value: V) { put(key, value) }

    /** Stores [value] only when [key] is absent and returns the existing value otherwise. */
    fun putIfAbsent(key: K, value: V): V? = synchronized(map) { map[key] ?: run { map[key] = value; null } }

    fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    /** Drops every entry whose key fails [keep]. */
    fun retainKeys(keep: (K) -> Boolean) { synchronized(map) { map.keys.retainAll(keep) } }

    fun containsKey(key: K): Boolean = synchronized(map) { map.containsKey(key) }
    fun clear() = synchronized(map) { map.clear() }

    /** Snapshot of values, least recently used first. */
    fun values(): List<V> = synchronized(map) { map.values.toList() }

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
