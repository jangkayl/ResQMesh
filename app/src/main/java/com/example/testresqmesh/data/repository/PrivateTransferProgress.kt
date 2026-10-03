package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.network.OutboundFrameEvent

/** Recipient timing belongs to the current transmission and starts after wire/hop completion. */
internal data class PrivateTransferProgress(
    val transmissionId: String,
    val hops: Int,
    val stage: OutboundFrameEvent.Stage? = null,
    val completedAt: Long = 0,
    val transferMs: Long = 0
) {
    fun observe(event: OutboundFrameEvent, now: Long): PrivateTransferProgress {
        if (event.transmissionId != transmissionId) return this
        // A socket thread may start before the caller's QUEUED event reaches the repository.
        if (event.stage == OutboundFrameEvent.Stage.QUEUED && stage != null) return this
        if (stage == OutboundFrameEvent.Stage.COMPLETED) return this
        return copy(stage = event.stage,
            completedAt = if (event.stage == OutboundFrameEvent.Stage.COMPLETED) now else completedAt,
            transferMs = maxOf(transferMs, event.transferMs))
    }

    fun shouldRetry(now: Long, acceptedAt: Long, retained: Boolean): Boolean {
        if (stage == OutboundFrameEvent.Stage.FAILED) return true
        if (stage == OutboundFrameEvent.Stage.COMPLETED) {
            val budget = (transferMs * (hops - 1).coerceAtLeast(0) * 2 + 15_000L).coerceIn(15_000L, 300_000L)
            return now - completedAt >= budget
        }
        return !retained && now - acceptedAt >= 60_000L
    }
}
