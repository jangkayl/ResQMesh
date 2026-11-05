package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PrivateTransferProgress
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.MAX_PRIVATE_ACCEPTED_ATTEMPTS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.PRIVATE_DELIVERY_TIMEOUT_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.PRIVATE_NO_PROGRESS_TIMEOUT_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.PRIVATE_RECEIPT_POLL_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.STRANDED_ORIGIN_MS
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Follows an accepted private send until its recipient receipt arrives or a timeout retries or fails it. */
internal class PrivateReceiptWatch(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val scope: CoroutineScope,
    private val requestRetry: () -> Unit,
    // The route a fresh transmission of a message would take now; null when its target is unknown.
    private val routeNow: suspend (String) -> List<String>? = { null }
) {
    private val acceptedAttempts = ConcurrentHashMap<String, Int>()
    private val timeoutOwners = ConcurrentHashMap<String, Any>()
    private val progress = ConcurrentHashMap<String, PrivateTransferProgress>()
    // Route of the last dispatched transmission; outlives [progress] so a later outbox pass can compare it.
    private val sentRoutes = ConcurrentHashMap<String, List<String>>()
    // Transmissions this watch already gave up on; their late frame events must not revive the message.
    private val retired = ConcurrentHashMap<String, Set<String>>()

    suspend fun onOutboundFrame(event: OutboundFrameEvent) {
        if (retired[event.messageId]?.contains(event.transmissionId) == true) return
        // A recovered journal can resume before the repository has registered a send.
        if (!timeoutOwners.containsKey(event.messageId) && messageStore.isOutstandingPrivateSend(event.messageId)) {
            progress.putIfAbsent(event.messageId, PrivateTransferProgress(event.transmissionId, event.hops)
                .observe(event, monotonicMs()))
            messageStore.markSent(event.messageId)
            // A resumed journal copy is not a fresh transmission, so it does not spend an attempt.
            awaitReceipt(event.messageId, freshTransmission = false)
        }
        progress.computeIfPresent(event.messageId) { _, current -> current.observe(event, monotonicMs()) }
    }

    fun beginTransfer(messageId: String, transmissionId: String, route: List<String>, media: Boolean) {
        val previous = progress.put(messageId,
            PrivateTransferProgress(transmissionId, route.size - 1, route = route, media = media))
        if (previous?.transmissionId != transmissionId) retired.retire(messageId, previous?.transmissionId)
    }

    /** Gives a manually retried message a fresh attempt budget. */
    fun resetAttempts(messageId: String) {
        acceptedAttempts.remove(messageId)
    }

    /** Drops all watch state; a running timeout loop sees its owner gone and settles nothing. */
    fun forget(messageId: String) {
        timeoutOwners.remove(messageId); progress.remove(messageId); acceptedAttempts.remove(messageId)
        sentRoutes.remove(messageId); retired.remove(messageId)
    }

    /** The route the last dispatched transmission of [messageId] was sent on, if known. */
    fun sentRoute(messageId: String): List<String>? = sentRoutes[messageId]

    fun abandonTransfer(messageId: String) {
        progress.remove(messageId)
    }

    /** Only a [freshTransmission] that was actually dispatched counts toward the attempt limit. */
    fun awaitReceipt(messageId: String, freshTransmission: Boolean = true) {
        if (freshTransmission) {
            acceptedAttempts.merge(messageId, 1, Int::plus)
            progress[messageId]?.route?.let { sentRoutes[messageId] = it }
        }
        val owner = Any()
        timeoutOwners[messageId] = owner
        scope.launch {
            if (networkManager.reportsOutboundProgress) waitForProgressVerdict(messageId, owner)
            else delay(PRIVATE_DELIVERY_TIMEOUT_MS)
            if (timeoutOwners[messageId] !== owner) return@launch
            // Retire before releasing ownership, so a late event of this transmission is already ignored.
            retired.retire(messageId, progress[messageId]?.transmissionId)
            if (!timeoutOwners.remove(messageId, owner)) return@launch
            progress.remove(messageId)
            settleUnanswered(messageId)
        }
    }

    private suspend fun waitForProgressVerdict(messageId: String, owner: Any) {
        val acceptedAt = monotonicMs()
        while (timeoutOwners[messageId] === owner) {
            delay(PRIVATE_RECEIPT_POLL_MS)
            if (!messageStore.isUnacknowledgedPrivateSend(messageId)) break
            val current = progress[messageId]
            val now = monotonicMs()
            val route = if (current?.stage == OutboundFrameEvent.Stage.COMPLETED) routeNow(messageId) else null
            val retained = networkManager.hasPendingTransfer(messageId)
            if (current?.shouldRetry(now, acceptedAt, retained, route) == true) break
            if (current != null && releasedStranded(messageId, current)) break
            if (current == null && !networkManager.hasPendingTransfer(messageId) &&
                now - acceptedAt >= PRIVATE_NO_PROGRESS_TIMEOUT_MS) break
        }
    }

    /**
     * A retained flight that never reached hop custody is pinned to its first hop. Once that hop has been gone
     * for [STRANDED_ORIGIN_MS] and a different route exists, the transport retires it so the outbox reroutes.
     * The synchronous release clears hasPendingTransfer, so the later outbox pass does not release it again.
     */
    private suspend fun releasedStranded(messageId: String, current: PrivateTransferProgress): Boolean {
        val settled = current.stage in setOf(OutboundFrameEvent.Stage.COMPLETED, OutboundFrameEvent.Stage.FAILED)
        val route = if (!settled && networkManager.hasPendingTransfer(messageId)) routeNow(messageId) else null
        val rerouted = !route.isNullOrEmpty() && route != current.route
        val released = rerouted && networkManager.releaseStrandedTransfer(messageId, STRANDED_ORIGIN_MS)
        if (released) AppLogger.d("MeshNetwork_E2EE", "PRIVATE_REROUTE message=$messageId first hop released")
        return released
    }

    private suspend fun settleUnanswered(messageId: String) {
        if (!messageStore.isUnacknowledgedPrivateSend(messageId)) {
            acceptedAttempts.remove(messageId)
            sentRoutes.remove(messageId)
            return
        }
        if ((acceptedAttempts[messageId] ?: 0) >= MAX_PRIVATE_ACCEPTED_ATTEMPTS) {
            messageStore.markFailed(messageId)
            PacketTraceEmitter.localOutcome(
                messageId, TraceScope.PRIVATE, TraceType.OTHER, TraceDropReason.RETRIES_EXHAUSTED
            )
            acceptedAttempts.remove(messageId)
            sentRoutes.remove(messageId)
        } else {
            messageStore.markPending(messageId)
            AppLogger.d("MeshNetwork_E2EE", "PRIVATE_RETRY message=$messageId awaiting recipient receipt")
            requestRetry()
        }
    }

}

private const val MAX_RETIRED_TRANSMISSIONS = 8

private fun ConcurrentHashMap<String, Set<String>>.retire(messageId: String, transmissionId: String?) {
    if (transmissionId == null) return
    merge(messageId, setOf(transmissionId)) { old, new ->
        (old + new).toList().takeLast(MAX_RETIRED_TRANSMISSIONS).toSet()
    }
}

private const val NANOS_PER_MILLI = 1_000_000L

private fun monotonicMs() = System.nanoTime() / NANOS_PER_MILLI
