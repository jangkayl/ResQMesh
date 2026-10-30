package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.network.bluetooth.state.OutboundQueuePolicy

/** Owns only rejected forwarding. Accepted frames belong exclusively to the chosen transport. */
class RelayForwarder(
    private val isActive: () -> Boolean,
    private val endpointForNode: (String) -> String?,
    private val dispatch: (String, ByteArray, Boolean) -> TransportDispatchResult,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val log: (String) -> Unit = {}
) {
    private data class Key(val transmission: String, val node: String)
    private class Work(val payload: ByteArray, val priority: Boolean, val expiresAt: Long) {
        var attempts = 1
        var lastAttemptAt = 0L
        var nextAt = 0L
    }
    private val pending = linkedMapOf<Key, Work>()
    private var timerToken = 0L
    private var timerAt: Long? = null

    @Synchronized fun forward(id: String, nodeId: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        if (!isActive() || id.isBlank() || nodeId.isBlank()) return TransportDispatchResult.REJECTED_NOT_READY
        val key = Key(id, nodeId.uppercase())
        // A repeated inbound transmission must not create an independent retry owner.
        if (pending.containsKey(key)) return TransportDispatchResult.REJECTED_QUEUE_FULL
        val result = send(key, bytes, priority)
        if (!retryable(result)) return result
        val retainedBytes = pending.values.sumOf { it.payload.size.toLong() + Int.SIZE_BYTES }
        if (!OutboundQueuePolicy.canAccept(pending.size, retainedBytes, bytes.size + Int.SIZE_BYTES, priority)) {
            log("RELAY_OVERFLOW transmission=$id nextHop=${key.node}")
            return result
        }
        val at = now()
        pending[key] = Work(bytes, priority, at + WORK_DEADLINE_MS).also {
            it.lastAttemptAt = at
            it.nextAt = at + FIRST_RETRY_MS
        }
        log("RELAY_RETAINED transmission=$id nextHop=${key.node} reason=$result")
        arm()
        return result
    }

    private fun send(key: Key, bytes: ByteArray, priority: Boolean): TransportDispatchResult =
        endpointForNode(key.node)?.let { dispatch(it, bytes, priority) } ?: TransportDispatchResult.REJECTED_NOT_READY

    private fun retryable(result: TransportDispatchResult) = result in setOf(
        TransportDispatchResult.REJECTED_QUEUE_FULL, TransportDispatchResult.REJECTED_NOT_READY,
        TransportDispatchResult.REJECTED_INVALID_ENDPOINT
    )

    @Synchronized fun wake() {
        val at = now()
        pending.values.forEach { it.nextAt = minOf(it.nextAt, maxOf(at, it.lastAttemptAt + MIN_ATTEMPT_SPACING_MS)) }
        arm()
    }

    @Synchronized private fun flush() {
        if (!isActive()) { clear(); return }
        val at = now()
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (key, work) = iterator.next()
            if (at >= work.expiresAt) {
                iterator.remove()
                log("RELAY_EXPIRED transmission=${key.transmission} nextHop=${key.node}")
            } else if (at >= work.nextAt) {
                val result = send(key, work.payload, work.priority)
                work.attempts++
                work.lastAttemptAt = at
                log("RELAY_RETRY transmission=${key.transmission} nextHop=${key.node} attempt=${work.attempts} result=$result")
                if (!retryable(result)) iterator.remove()
                else work.nextAt = at + (FIRST_RETRY_MS shl (work.attempts - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)).coerceAtMost(MAX_RETRY_MS)
            }
        }
        arm()
    }

    private fun arm() {
        val deadline = pending.values.minOfOrNull { minOf(it.nextAt, it.expiresAt) }
        if (deadline == null) { timerAt = null; timerToken++; return }
        if (timerAt?.let { it <= deadline } == true) return
        timerAt = deadline
        val token = ++timerToken
        schedule((deadline - now()).coerceAtLeast(0L)) {
            synchronized(this) {
                if (token != timerToken) return@synchronized
                timerAt = null
                flush()
            }
        }
    }

    @Synchronized fun clear() {
        if (pending.isNotEmpty()) log("RELAY_CLEARED count=${pending.size}")
        pending.clear()
        timerAt = null
        timerToken++
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val WORK_DEADLINE_MS = 10_000L
private const val FIRST_RETRY_MS = 250L
private const val MIN_ATTEMPT_SPACING_MS = 100L
private const val MAX_RETRY_MS = 2_000L
private const val MAX_BACKOFF_DOUBLINGS = 3
