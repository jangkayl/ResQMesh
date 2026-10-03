package com.example.testresqmesh.core.network.bluetooth.state

/** Owns already accepted socket frames until GATT has capacity. New ordinary sends must wait. */
class RetainedFallbackQueue<T>(
    private val send: (String, T) -> Boolean,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val failed: (String, T) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private data class Entry<T>(val item: T, val expiresAt: Long)
    private val pending = linkedMapOf<String, ArrayDeque<Entry<T>>>()
    private var timer = false

    @Synchronized fun retain(endpoint: String, frames: List<T>) {
        if (frames.isEmpty()) return
        val queue = pending.getOrPut(endpoint) { ArrayDeque() }
        frames.forEach { queue.addLast(Entry(it, now() + 24 * 60 * 60 * 1000L)) }
        flush()
    }

    @Synchronized fun hasPending(endpoint: String) = pending[endpoint]?.isNotEmpty() == true

    @Synchronized fun flush() {
        val at = now()
        pending.entries.toList().forEach { (endpoint, queue) ->
            while (queue.isNotEmpty()) {
                val entry = queue.first()
                if (at >= entry.expiresAt) { queue.removeFirst(); failed(endpoint, entry.item) }
                else if (send(endpoint, entry.item)) queue.removeFirst()
                else break
            }
            if (queue.isEmpty()) pending.remove(endpoint)
        }
        if (!timer && pending.isNotEmpty()) {
            timer = true
            schedule(200L) { synchronized(this) { timer = false; flush() } }
        }
    }

    @Synchronized fun retire(endpoint: String) {
        pending.remove(endpoint)?.forEach { failed(endpoint, it.item) }
    }

    @Synchronized fun clear() { pending.keys.toList().forEach(::retire) }
}
