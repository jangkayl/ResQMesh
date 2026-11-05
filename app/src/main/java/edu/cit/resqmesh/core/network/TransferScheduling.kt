package edu.cit.resqmesh.core.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Our own first-hop flights by flight key. The engine writes it under its monitor (offer, reservation release)
 * or on the transfer worker (peer readiness); callers read it synchronously, so release and cancel can answer
 * without waiting on the worker.
 */
internal class OriginRegistry {
    private data class Watch(val messageId: String, val notReadySince: Long = 0L)

    private val watches = ConcurrentHashMap<String, Watch>()

    fun register(key: String, messageId: String) { watches[key] = Watch(messageId) }
    fun remove(key: String) { watches.remove(key) }

    /** Mirrors a flight's readiness; an entry already released stays released. */
    fun awaySince(key: String, since: Long) {
        watches.computeIfPresent(key) { _, watch -> watch.copy(notReadySince = since) }
    }

    fun keysOf(messageId: String): Set<String> = watches.filterValues { it.messageId == messageId }.keys

    /** Keys of [messageId] flights whose peer has been away for at least [minAwayMs] at [at]. */
    fun strandedKeys(messageId: String, at: Long, minAwayMs: Long): Set<String> = watches.filterValues {
        it.messageId == messageId && it.notReadySince > 0 && at - it.notReadySince >= minAwayMs
    }.keys
}

/**
 * Large ordinary flights go to each peer oldest first, so a newer image cannot overtake or split the link with
 * an older one. A head that is stalled after a re-probe, or whose status query is long overdue, lets the next
 * one run alongside it until it progresses again. Priority, small and late-relay flights are never held.
 */
internal object MediaFifo {
    /** Ordinary flights above this size are media and go to each peer one at a time, oldest first. */
    const val MEDIA_FIFO_BYTES = 16 * 1024

    fun held(flights: Collection<TransferFlight>, at: Long, stallMs: Long, queryMs: Long): Set<String> = flights
        .filter { !it.record.priority && !it.record.lateRelay && it.record.size > MEDIA_FIFO_BYTES }
        .groupBy { it.record.peer }.values.flatMap { media ->
            val queue = media.sortedBy { it.createdAt }
            var running = 1
            while (running < queue.size && headBlocked(queue[running - 1], at, stallMs, queryMs)) running++
            queue.drop(running).map { it.key }
        }.toSet()

    private fun headBlocked(flight: TransferFlight, at: Long, stallMs: Long, queryMs: Long) =
        (flight.reprobes > 0 && at - flight.progressAt >= stallMs) ||
            (flight.waitingStatus && at - flight.queryStartedAt >= queryMs)
}
