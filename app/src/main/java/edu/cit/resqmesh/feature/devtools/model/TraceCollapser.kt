package edu.cit.resqmesh.feature.devtools.model

import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.logging.trace.TraceType

/** One displayed row: [last] is the newest of [count] collapsed events; [first] the oldest. */
data class TraceRow(val first: PacketTrace, val last: PacketTrace, val count: Int)

/** Pure display collapse over adjacent rows; search and follow-packet use the raw events instead. */
object TraceCollapser {
    private val collapsible = setOf(TraceType.SYSTEM, TraceType.PING, TraceType.PONG, TraceType.PIECE, TraceType.ACK)

    fun isFailure(t: PacketTrace): Boolean =
        t.stage == TraceStage.FAILED || t.stage == TraceStage.DROPPED || t.dropReason != TraceDropReason.NONE

    private fun peer(t: PacketTrace): String? = t.sourceId ?: t.destId ?: t.nextHop

    private fun canJoin(row: TraceRow, next: PacketTrace): Boolean {
        val a = row.last
        return a.type in collapsible && !isFailure(a) && !isFailure(next) &&
            a.type == next.type && a.dir == next.dir && peer(a) == peer(next)
    }

    /** [events] in chronological order; the result keeps that order. */
    fun collapse(events: List<PacketTrace>): List<TraceRow> {
        val out = ArrayList<TraceRow>()
        for (e in events) {
            val prev = out.lastOrNull()
            if (prev != null && canJoin(prev, e)) {
                out[out.lastIndex] = TraceRow(prev.first, e, prev.count + 1)
            } else {
                out.add(TraceRow(e, e, 1))
            }
        }
        return out
    }
}
