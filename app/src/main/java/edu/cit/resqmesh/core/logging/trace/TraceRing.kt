package edu.cit.resqmesh.core.logging.trace

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray

/** Lock-free fixed ring: writers claim a slot with one atomic increment and overwrite the oldest. */
class TraceRing<T : Any>(val capacity: Int) {
    private val slots = AtomicReferenceArray<T?>(capacity)
    private val index = AtomicLong(0)

    val written: Long get() = index.get()

    fun add(item: T) {
        val i = index.getAndIncrement()
        slots.set((i % capacity).toInt(), item)
    }

    /** Oldest to newest. A slot being overwritten concurrently may be skipped or newer; never throws. */
    fun snapshot(): List<T> {
        val end = index.get()
        val start = maxOf(0L, end - capacity)
        val out = ArrayList<T>((end - start).toInt())
        for (i in start until end) {
            slots.get((i % capacity).toInt())?.let(out::add)
        }
        return out
    }

    fun clear() {
        for (i in 0 until capacity) slots.set(i, null)
    }
}
