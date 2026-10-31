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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Follows an accepted private send until its recipient receipt arrives or a timeout retries or fails it. */
internal class PrivateReceiptWatch(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val scope: CoroutineScope,
    private val requestRetry: () -> Unit
) {
    private val acceptedAttempts = ConcurrentHashMap<String, Int>()
    private val timeoutOwners = ConcurrentHashMap<String, Any>()
    private val progress = ConcurrentHashMap<String, PrivateTransferProgress>()

    suspend fun onOutboundFrame(event: OutboundFrameEvent) {
        // A recovered journal can resume before the repository has registered a send.
        if (!timeoutOwners.containsKey(event.messageId) && messageStore.isOutstandingPrivateSend(event.messageId)) {
            progress.putIfAbsent(event.messageId, PrivateTransferProgress(event.transmissionId, event.hops)
                .observe(event, monotonicMs()))
            messageStore.markSent(event.messageId)
            awaitReceipt(event.messageId)
        }
        progress.computeIfPresent(event.messageId) { _, current -> current.observe(event, monotonicMs()) }
    }

    fun beginTransfer(messageId: String, transmissionId: String, hops: Int) {
        progress[messageId] = PrivateTransferProgress(transmissionId, hops)
    }

    fun abandonTransfer(messageId: String) {
        progress.remove(messageId)
    }

    fun awaitReceipt(messageId: String) {
        acceptedAttempts.merge(messageId, 1, Int::plus)
        val owner = Any()
        timeoutOwners[messageId] = owner
        scope.launch {
            if (networkManager.reportsOutboundProgress) waitForProgressVerdict(messageId, owner)
            else delay(PRIVATE_DELIVERY_TIMEOUT_MS)
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
            if (current?.shouldRetry(now, acceptedAt, networkManager.hasPendingTransfer(messageId)) == true) break
            if (current == null && !networkManager.hasPendingTransfer(messageId) &&
                now - acceptedAt >= PRIVATE_NO_PROGRESS_TIMEOUT_MS) break
        }
    }

    private suspend fun settleUnanswered(messageId: String) {
        if (!messageStore.isUnacknowledgedPrivateSend(messageId)) {
            acceptedAttempts.remove(messageId)
            return
        }
        if ((acceptedAttempts[messageId] ?: 0) >= MAX_PRIVATE_ACCEPTED_ATTEMPTS) {
            messageStore.markFailed(messageId)
            PacketTraceEmitter.localOutcome(
                messageId, TraceScope.PRIVATE, TraceType.OTHER, TraceDropReason.RETRIES_EXHAUSTED
            )
            acceptedAttempts.remove(messageId)
        } else {
            messageStore.markPending(messageId)
            AppLogger.d("MeshNetwork_E2EE", "PRIVATE_RETRY message=$messageId awaiting recipient receipt")
            requestRetry()
        }
    }

    private fun monotonicMs() = System.nanoTime() / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
