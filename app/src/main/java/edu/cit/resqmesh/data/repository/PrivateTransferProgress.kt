package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.PRIVATE_CUSTODY_WAIT_MS

/**
 * Recipient timing belongs to the current transmission and starts after wire/hop completion.
 * [route] is the directed node list this transmission was sent on; [media] marks image/audio payloads, whose
 * first hop keeps a durable custody copy, so an unchanged route waits for that relay instead of re-uploading.
 */
internal data class PrivateTransferProgress(
    val transmissionId: String,
    val hops: Int,
    val stage: OutboundFrameEvent.Stage? = null,
    val completedAt: Long = 0,
    val transferMs: Long = 0,
    val route: List<String> = emptyList(),
    val media: Boolean = false
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

    /** [routeNow] is the route a fresh transmission would take now, or null when it is unknown. */
    fun shouldRetry(now: Long, acceptedAt: Long, retained: Boolean, routeNow: List<String>? = null): Boolean {
        return when (stage) {
            OutboundFrameEvent.Stage.FAILED -> true
            OutboundFrameEvent.Stage.COMPLETED -> completedVerdict(now, routeNow)
            else -> !retained && now - acceptedAt >= 60_000L
        }
    }

    private fun completedVerdict(now: Long, routeNow: List<String>?): Boolean {
        val current = routeNow?.takeIf { route.isNotEmpty() }
        val budget = (transferMs * (hops - 1).coerceAtLeast(0) * 2 + 15_000L).coerceIn(15_000L, 300_000L)
        return when {
            // A hop vanished and a different path exists: the relay copy cannot help, resend along the new path.
            !current.isNullOrEmpty() && current != route -> true
            // The relay owns a durable copy that resumes on reconnect; re-uploading would only duplicate it.
            current != null && media -> now - completedAt >= PRIVATE_CUSTODY_WAIT_MS
            else -> now - completedAt >= budget
        }
    }
}
