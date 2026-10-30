package edu.cit.resqmesh.feature.devtools.model

import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceType

/** One timeline line of this phone's own events for a packet key. */
data class TimelineEntry(val event: PacketTrace, val label: String, val isReceipt: Boolean, val isFailure: Boolean)

/** Pure timeline join over raw events: same packet key, chronological, capped. Receipts share the key by design. */
object TraceTimeline {
    const val MAX_ENTRIES = 200

    private val receiptTypes = setOf(TraceType.SEEN, TraceType.DELIVERED)

    fun build(events: List<PacketTrace>, packetKey: String, cap: Int = MAX_ENTRIES): List<TimelineEntry> {
        if (packetKey.isEmpty()) return emptyList()
        val matched = events.filter { it.packetKey == packetKey }.sortedBy { it.seq }
        // Keep the newest entries when capped; the oldest context is the least useful.
        return matched.takeLast(cap).map { e ->
            TimelineEntry(e, labelOf(e), e.type in receiptTypes, isFailure(e))
        }
    }

    private fun isFailure(e: PacketTrace) =
        e.stage == TraceStage.FAILED || e.stage == TraceStage.DROPPED || e.dropReason != TraceDropReason.NONE

    /** Relayed: a public packet with hops already used, or a private one sent past the first step of its route. */
    fun isRelayed(e: PacketTrace): Boolean = when {
        e.scope == TraceScope.PUBLIC -> (e.hop ?: 0) > 0
        e.scope == TraceScope.PRIVATE -> (e.routeStep ?: 0) > 1
        else -> false
    }

    fun labelOf(e: PacketTrace): String {
        val reason = if (e.dropReason != TraceDropReason.NONE) " (${e.dropReason.name})" else ""
        val step = e.routeStep?.let { s -> e.pathLen?.let { " step $s/${it - 1}" } }.orEmpty()
        val base = when {
            e.type in receiptTypes -> "Receipt ${e.type.name}" + if (e.dir == TraceDir.IN) " received" else " sent"
            e.stage == TraceStage.DROPPED -> "Dropped"
            e.stage == TraceStage.FAILED -> "Failed"
            e.dir == TraceDir.IN -> "Received"
            e.stage == TraceStage.QUEUED -> "Queued"
            isRelayed(e) -> "Relayed"
            else -> "Sent"
        }
        return base + reason + step
    }
}
