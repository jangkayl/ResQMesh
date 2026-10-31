package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import edu.cit.resqmesh.core.network.BroadcastDispatchResult
import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.core.network.TransportDispatchResult
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.network.TransferTraceEmitter
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceType
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import edu.cit.resqmesh.data.repository.MeshRouter
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PayloadFactory
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import edu.cit.resqmesh.data.repository.PrivateDeliveryPlanner
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.OUTBOX_RETRY_BACKOFF_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.OUTBOX_EXPIRY_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.MIN_BUDGET_RETRY_MS

/** The user-authored part of an outgoing chat message. */
internal data class OutgoingContent(
    val text: String,
    val imageBase64: String?,
    val audioBase64: String?,
    val locationLat: Double? = null,
    val locationLng: Double? = null
)

@Suppress("LongParameterList")
internal class MeshOutboundDelivery(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope,
    private val sosRepository: SosReplyCapability?,
    private val meshRouter: MeshRouter,
    private val _currentChannelId: MutableStateFlow<String>,
    private val _incomingSosAlert: MutableStateFlow<ChatMessage?>,
    private val _publicSendFeedback: kotlinx.coroutines.flow.MutableSharedFlow<String>,
    private val localName: () -> String,
    private val readyPeers: () -> List<ConnectedDevice>,
    private val resolveName: (String) -> String,
    private val chatBudget: OutgoingChatBudget = OutgoingChatBudget()
) {
    private val myNodeName get() = localName()
    private fun traceOutcome(id: String, scope: TraceScope, reason: TraceDropReason) =
        PacketTraceEmitter.localOutcome(id, scope, TraceType.OTHER, reason)

    /** The sender knows its own note is voice, even when private audio is encrypted on the wire. */
    private fun traceSentVoice(id: String, scope: TraceScope) =
        TransferTraceEmitter.voice(TraceDir.OUT, TraceStage.QUEUED, scope, id, TraceDropReason.NONE, "local")

    private fun scopeOf(targetName: String?) = if (targetName != null) TraceScope.PRIVATE else TraceScope.PUBLIC
    private fun readyConnectedDevices() = readyPeers()
    private fun currentPeerName(name: String) = resolveName(name)

    val outboxMutex = Mutex()
    private val outboxWake = Channel<Unit>(Channel.CONFLATED)
    private var outboxRetryJob: Job? = null
    private var outboxRetryDueAt = 0L
    // First sends were already charged by the ViewModel gate, so their first dispatch is exempt even when it
    // happens later from the outbox. Everything else (receipt retries, recovered rows) is charged per dispatch.
    private val prepaid = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val receipts = PrivateReceiptWatch(networkManager, messageStore, repositoryScope, ::scheduleOutboxFlush)

    /** What one outbox pass decided about retrying. */
    private class OutboxPass {
        var retryRejectedDispatch = false
        var budgetDeferred = false
    }

    fun startWorker() {
        repositoryScope.launch {
            outboxMutex.withLock { messageStore.recoverUnacknowledgedPrivateSends() }
            for (wake in outboxWake) flushOutbox()
        }
    }

    fun onNetworkOutboundFrame(event: OutboundFrameEvent): Unit = run {
        if (event.isPrivate) repositoryScope.launch {
            outboxMutex.withLock { receipts.onOutboundFrame(event) }
        }
    }

    fun onNetworkMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>): Unit = run {
        val currentSos = _incomingSosAlert.value
        if (currentSos?.id == msgId && !currentSos.deliveredTo.contains(readerName)) {
            _incomingSosAlert.value = currentSos.copy(deliveredTo = currentSos.deliveredTo + readerName)
        }

        repositoryScope.launch {
            messageStore.markDelivered(msgId, readerName)
            AppLogger.d("MeshNetwork_E2EE", "PRIVATE_DELIVERED message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        }
    }

    fun onNetworkMessageSeen(msgId: String, readerName: String): Unit = run {
        repositoryScope.launch {
            messageStore.markSeen(msgId, readerName)
            AppLogger.d("MeshNetwork_E2EE", "PRIVATE_SEEN message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        }
    }

    private fun awaitPrivateReceipt(messageId: String) = receipts.awaitReceipt(messageId)

    fun scheduleOutboxFlush() {
        // Conflate wakes without postponing or cancelling the serialized worker.
        outboxWake.trySend(Unit)
    }

    private fun scheduleOutboxRetry(delayMs: Long = OUTBOX_RETRY_BACKOFF_MS) {
        val dueAt = System.currentTimeMillis() + delayMs
        if (outboxRetryJob?.isActive == true) {
            if (outboxRetryDueAt <= dueAt) return
            outboxRetryJob?.cancel()
        }
        outboxRetryDueAt = dueAt
        outboxRetryJob = repositoryScope.launch {
            delay(delayMs)
            scheduleOutboxFlush()
        }
    }

    /** True when a dispatch of [messageId] may go out now: already paid by the send gate, or a token is taken. */
    private fun admitDispatch(messageId: String) = prepaid.contains(messageId) || chatBudget.tryConsume()

    private suspend fun flushOutbox() = outboxMutex.withLock {
        val now = System.currentTimeMillis()
        val pendingMessages = messageStore.getPendingOutbox()
        if (pendingMessages.isEmpty()) return

        val readyDevices = readyConnectedDevices()
        if (readyDevices.isEmpty()) return

        val pass = OutboxPass()
        for ((message, targetName) in pendingMessages) {
            if (isUnsendablePublic(message, targetName) || now - message.timestamp > OUTBOX_EXPIRY_MS) {
                expire(message.id)
                traceOutcome(message.id, scopeOf(targetName), TraceDropReason.EXPIRED_UNSENT)
                continue
            }
            if (targetName != null) retryPrivate(message, targetName, readyDevices, pass)
            else retryPublic(message, pass)
        }
        if (pass.retryRejectedDispatch) {
            scheduleOutboxRetry()
        } else if (pass.budgetDeferred) {
            chatBudget.refresh()
            scheduleOutboxRetry(chatBudget.state.value.nextRefillInMs.coerceAtLeast(MIN_BUDGET_RETRY_MS))
        }
    }

    private suspend fun isUnsendablePublic(message: ChatMessage, targetName: String?): Boolean =
        targetName == null && (message.conversationKind.startsWith("LEGACY_") || message.isSOS ||
            (message.conversationKind == "SOS" && sosRepository?.canReply(message.sosId) != true))

    private suspend fun expire(messageId: String) {
        messageStore.expirePending(messageId)
        prepaid.remove(messageId)
    }

    private suspend fun retryPrivate(message: ChatMessage, targetName: String, readyDevices: List<ConnectedDevice>, pass: OutboxPass) {
        // The transport journal already owns this note; do not create a fresh competing
        // transmission simply because a peer disappeared during its chunk transfer.
        if (networkManager.hasPendingTransfer(message.id)) return
        val currentTarget = currentPeerName(targetName)
        // A saved pending row must not bypass the same key-change refusal as a new send.
        if (publicKeys.hasPendingChange(currentTarget)) return
        val targetPubKey = publicKeys.trustedKey(currentTarget) ?: return
        val directedRouteList = meshRouter.findShortestPath(myNodeName, currentTarget, readyDevices)
        val payloadBytes = runCatching {
            PayloadFactory.buildPrivatePayload(
                msgId = message.id,
                timestamp = message.timestamp,
                senderName = myNodeName,
                targetName = currentTarget,
                text = message.text,
                imageBase64 = message.imageBase64,
                audioBase64 = message.audioBase64,
                locationLat = message.locationLat,
                locationLng = message.locationLng,
                directedRoute = directedRouteList,
                targetPubKey = targetPubKey,
                channelId = _currentChannelId.value,
                transmissionId = UUID.randomUUID().toString()
            )
        }.getOrNull() ?: return

        val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
        val transmission = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes).id }.getOrNull()
        if (transmission != null) receipts.beginTransfer(message.id, transmission, directedRouteList.size - 1)
        val dispatchResult = when (delivery) {
            is PrivateDeliveryPlanner.Target.Endpoint -> {
                if (admitDispatch(message.id)) {
                    networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
                } else {
                    pass.budgetDeferred = true // stays PENDING until a token refills
                    null
                }
            }
            PrivateDeliveryPlanner.Target.Unavailable -> null
        }
        if (dispatchResult?.accepted == true) {
            messageStore.markSent(message.id)
            prepaid.remove(message.id)
            awaitPrivateReceipt(message.id)
        } else {
            receipts.abandonTransfer(message.id)
            if (dispatchResult != null) pass.retryRejectedDispatch = true
        }
    }

    private suspend fun retryPublic(message: ChatMessage, pass: OutboxPass) {
        val payloadBytes = PayloadFactory.buildPublicPayload(
            msgId = message.id,
            timestamp = message.timestamp,
            senderName = myNodeName,
            text = message.text,
            imageBase64 = message.imageBase64,
            audioBase64 = message.audioBase64,
            locationLat = message.locationLat,
            locationLng = message.locationLng,
            isSOS = message.isSOS,
            isSOSCancel = false,
            channelId = message.channelId,
            conversationKind = message.conversationKind,
            sosId = message.sosId,
            senderNodeId = message.senderNodeId.ifBlank { networkManager.myNodeId },
            ttl = networkManager.currentMeshTtl()
        )
        if (!admitDispatch(message.id)) {
            pass.budgetDeferred = true // stays PENDING until a token refills
            return
        }
        val result = broadcast(payloadBytes, message.isSOS)
        if (settleBroadcast(message.id, result)) pass.retryRejectedDispatch = true
    }

    private fun broadcast(payloadBytes: ByteArray, isSOS: Boolean) =
        if (isSOS) networkManager.broadcastPriorityPayload(payloadBytes) else networkManager.broadcastPayload(payloadBytes)

    /** Applies a broadcast result to the stored row. True when it stays pending for a retry. */
    private suspend fun settleBroadcast(messageId: String, result: BroadcastDispatchResult): Boolean {
        if (result.anyAccepted) {
            messageStore.markSent(messageId)
            prepaid.remove(messageId)
            return false
        }
        if (result.neighbors.values.any { it == TransportDispatchResult.REJECTED_INVALID_FRAME }) {
            expire(messageId)
            traceOutcome(messageId, TraceScope.PUBLIC, TraceDropReason.SEND_INVALID_FRAME)
            return false
        }
        return true
    }

    /** Final guard: refuses text above the shared message limit. Logs field and length only, never content. */
    private fun rejectOverLimit(text: String): Boolean {
        if (text.length <= edu.cit.resqmesh.core.security.input.TextField.MESSAGE.maxLength) return false
        AppLogger.d("MeshNetwork", "INPUT_REJECT field=MESSAGE len=${text.length}")
        return true
    }

    fun sendPublicMessage(content: OutgoingContent, isSOS: Boolean = false, isSOSCancel: Boolean = false,
        conversationKind: String = "COMMUNITY", channelId: String = "", sosId: String = ""): String {
        require(!isSOS && !isSOSCancel) { "SOS lifecycle uses signed SosRepository operations" }
        require(conversationKind in setOf("COMMUNITY", "RADIO", "SOS"))
        if (rejectOverLimit(content.text)) return ""
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val payloadBytes = PayloadFactory.buildPublicPayload(
            msgId = messageId,
            timestamp = timestamp,
            senderName = myNodeName,
            text = content.text,
            imageBase64 = content.imageBase64,
            audioBase64 = content.audioBase64,
            locationLat = content.locationLat,
            locationLng = content.locationLng,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel,
            channelId = channelId,
            conversationKind = conversationKind,
            sosId = sosId,
            senderNodeId = networkManager.myNodeId,
            ttl = networkManager.currentMeshTtl()
        )

        val message = ChatMessage(messageId, myNodeName, content.text, content.imageBase64, content.audioBase64,
            content.locationLat, content.locationLng, true, false, timestamp,
            conversationKind = conversationKind, channelId = channelId, sosId = sosId, senderNodeId = networkManager.myNodeId)
        if (content.audioBase64 != null) traceSentVoice(messageId, TraceScope.PUBLIC)
        prepaid.add(messageId)
        repositoryScope.launch {
            outboxMutex.withLock {
                if (conversationKind == "SOS" && sosRepository?.canReply(sosId) != true) {
                    _publicSendFeedback.emit("This SOS has ended. Its conversation is read-only.")
                    return@withLock
                }
                // Persist first, so acceptance and receipts cannot race an absent database row.
                messageStore.save(message.copy(deliveredTo = listOf("PENDING")), targetName = null)
                val result = if (readyConnectedDevices().isEmpty()) BroadcastDispatchResult(emptyMap())
                    else broadcast(payloadBytes, isSOS)
                if (settleBroadcast(messageId, result)) scheduleOutboxRetry()
                result.feedback()?.let { _publicSendFeedback.emit(it) }
            }
        }
        return messageId
    }

    fun sendPrivateMessage(targetName: String, content: OutgoingContent): Boolean {
        if (rejectOverLimit(content.text)) return false
        val msgId = UUID.randomUUID().toString()
        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_SEND_REQUEST message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        val timestamp = System.currentTimeMillis()
        val currentTarget = currentPeerName(targetName)
        if (publicKeys.hasPendingChange(currentTarget)) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: recipient key change requires approval")
            return false
        }

        prepaid.add(msgId)
        val readyDevices = readyConnectedDevices()
        val directedRouteList = if (readyDevices.isNotEmpty()) meshRouter.findShortestPath(myNodeName, currentTarget, readyDevices) else emptyList()
        val targetPubKey = publicKeys.trustedKey(currentTarget)

        // If we are completely offline or route is unavailable, save as Pending outbox message
        if (readyDevices.isEmpty() || targetPubKey == null || directedRouteList.isEmpty()) {
            queueUnroutedPrivate(msgId, timestamp, currentTarget, content)
            AppLogger.d(
                "MeshNetwork_E2EE",
                "Private send queued as Pending for ${AppLogger.redactName(targetName)} (no immediate route)"
            )
            return true
        }

        val payloadBytes = runCatching { PayloadFactory.buildPrivatePayload(
            msgId = msgId,
            timestamp = timestamp,
            senderName = myNodeName,
            targetName = currentTarget,
            text = content.text,
            imageBase64 = content.imageBase64,
            audioBase64 = content.audioBase64,
            locationLat = content.locationLat,
            locationLng = content.locationLng,
            directedRoute = directedRouteList,
            targetPubKey = targetPubKey,
            channelId = _currentChannelId.value
        ) }.getOrElse {
            AppLogger.d(
                "MeshNetwork_E2EE",
                "Private message to ${AppLogger.redactName(targetName)} was not sent: " +
                    "recipient key unavailable or encryption failed"
            )
            prepaid.remove(msgId)
            return false
        }

        if (content.audioBase64 != null) traceSentVoice(msgId, TraceScope.PRIVATE)
        val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
        val isDirect = readyDevices.any { NodeIdentity.matches(it.name, currentTarget) }
        val message = ChatMessage(msgId, myNodeName, content.text, content.imageBase64, content.audioBase64,
            content.locationLat, content.locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        repositoryScope.launch { persistAndDispatchPrivate(message, currentTarget, delivery, payloadBytes) }
        return true
    }

    private fun queueUnroutedPrivate(msgId: String, timestamp: Long, currentTarget: String, content: OutgoingContent) {
        val message = ChatMessage(
            id = msgId,
            senderName = myNodeName,
            text = content.text,
            imageBase64 = content.imageBase64,
            audioBase64 = content.audioBase64,
            locationLat = content.locationLat,
            locationLng = content.locationLng,
            isMine = true,
            isPrivate = true,
            timestamp = timestamp,
            isHopped = false,
            outboundRoute = emptyList()
        )
        repositoryScope.launch {
            messageStore.save(message, targetName = currentTarget)
            messageStore.markPending(msgId)
            scheduleOutboxFlush()
        }
    }

    private suspend fun persistAndDispatchPrivate(message: ChatMessage, currentTarget: String,
        delivery: PrivateDeliveryPlanner.Target, payloadBytes: ByteArray) = outboxMutex.withLock {
        val msgId = message.id
        val hops = message.outboundRoute.size - 1
        // Persist before dispatch so a fast receipt cannot race the local message row.
        messageStore.save(message, targetName = currentTarget)
        messageStore.markPending(msgId)
        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_PERSISTED message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        receipts.beginTransfer(msgId, msgId, hops)
        val dispatchResult = when (delivery) {
            is PrivateDeliveryPlanner.Target.Endpoint -> {
                AppLogger.d("MeshNetwork_E2EE", "Private route selected with $hops hop(s)")
                networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
            }
            PrivateDeliveryPlanner.Target.Unavailable -> null
        }
        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_DISPATCH message=$msgId result=$dispatchResult elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        if (dispatchResult?.accepted == true) {
            messageStore.markSent(msgId)
            prepaid.remove(msgId)
            awaitPrivateReceipt(msgId)
        } else {
            receipts.abandonTransfer(msgId)
            messageStore.markPending(msgId)
            AppLogger.d("MeshNetwork_E2EE", "Private dispatch was not accepted; retained for directed retry")
            scheduleOutboxRetry()
        }
    }
}
