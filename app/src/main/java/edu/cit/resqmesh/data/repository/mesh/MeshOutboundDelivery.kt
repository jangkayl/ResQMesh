package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import edu.cit.resqmesh.core.network.OutboundFrameEvent
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.logging.AppLogger
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
import edu.cit.resqmesh.data.repository.PrivateTransferProgress
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.PRIVATE_DELIVERY_TIMEOUT_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.OUTBOX_RETRY_BACKOFF_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.OUTBOX_EXPIRY_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.MIN_BUDGET_RETRY_MS

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
    private fun readyConnectedDevices() = readyPeers()
    private fun currentPeerName(name: String) = resolveName(name)

    val outboxMutex = Mutex()
    private val outboxWake = Channel<Unit>(Channel.CONFLATED)
    private var outboxRetryJob: Job? = null
    private var outboxRetryDueAt = 0L
    // First sends were already charged by the ViewModel gate, so their first dispatch is exempt even when it
    // happens later from the outbox. Everything else (receipt retries, recovered rows) is charged per dispatch.
    private val prepaid = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val privateAcceptedAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val privateTimeoutOwners = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val privateProgress = java.util.concurrent.ConcurrentHashMap<String, PrivateTransferProgress>()

    fun startWorker() {
        repositoryScope.launch {
            outboxMutex.withLock { messageStore.recoverUnacknowledgedPrivateSends() }
            for (wake in outboxWake) flushOutbox()
        }
    }

    fun onNetworkOutboundFrame(event: OutboundFrameEvent): Unit = run {
        if (event.isPrivate) repositoryScope.launch {
            outboxMutex.withLock {
                // A recovered journal can resume before the repository has registered a send.
                if (!privateTimeoutOwners.containsKey(event.messageId) && messageStore.isOutstandingPrivateSend(event.messageId)) {
                    privateProgress.putIfAbsent(event.messageId, PrivateTransferProgress(event.transmissionId, event.hops)
                        .observe(event, System.nanoTime() / 1_000_000))
                    messageStore.markSent(event.messageId)
                    awaitPrivateReceipt(event.messageId)
                }
                privateProgress.computeIfPresent(event.messageId) { _, progress ->
                    progress.observe(event, System.nanoTime() / 1_000_000)
                }
            }
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

    private fun awaitPrivateReceipt(messageId: String) {
        privateAcceptedAttempts.merge(messageId, 1, Int::plus)
        val owner = Any()
        privateTimeoutOwners[messageId] = owner
        repositoryScope.launch {
            val acceptedAt = System.nanoTime() / 1_000_000
            if (networkManager.reportsOutboundProgress) {
                while (privateTimeoutOwners[messageId] === owner) {
                    delay(1_000L)
                    if (!messageStore.isUnacknowledgedPrivateSend(messageId)) break
                    val progress = privateProgress[messageId]
                    val now = System.nanoTime() / 1_000_000
                    if (progress?.shouldRetry(now, acceptedAt, networkManager.hasPendingTransfer(messageId)) == true) break
                    if (progress == null && !networkManager.hasPendingTransfer(messageId) && now - acceptedAt >= 60_000L) break
                }
            } else delay(PRIVATE_DELIVERY_TIMEOUT_MS)
            if (!privateTimeoutOwners.remove(messageId, owner)) return@launch
            privateProgress.remove(messageId)
            if (!messageStore.isUnacknowledgedPrivateSend(messageId)) {
                privateAcceptedAttempts.remove(messageId)
                return@launch
            }
            if ((privateAcceptedAttempts[messageId] ?: 0) >= 3) {
                messageStore.markFailed(messageId)
                privateAcceptedAttempts.remove(messageId)
            } else {
                messageStore.markPending(messageId)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_RETRY message=$messageId awaiting recipient receipt")
                scheduleOutboxFlush()
            }
        }
    }

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

        var retryRejectedDispatch = false
        var budgetDeferred = false
        for ((message, targetName) in pendingMessages) {
            if (targetName == null && (message.conversationKind.startsWith("LEGACY_") || message.isSOS ||
                    (message.conversationKind == "SOS" && sosRepository?.canReply(message.sosId) != true))) {
                messageStore.expirePending(message.id)
                continue
            }
            if (now - message.timestamp > OUTBOX_EXPIRY_MS) {
                messageStore.expirePending(message.id)
                continue
            }
            if (targetName != null) {
                // The transport journal already owns this note; do not create a fresh competing
                // transmission simply because a peer disappeared during its chunk transfer.
                if (networkManager.hasPendingTransfer(message.id)) continue
                // Private message retry
                val currentTarget = currentPeerName(targetName)
                // A saved pending row must not bypass the same key-change refusal as a new send.
                if (publicKeys.hasPendingChange(currentTarget)) continue
                val targetPubKey = publicKeys.trustedKey(currentTarget) ?: continue
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
                }.getOrNull() ?: continue

                val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
                val transmission = runCatching { ProtoBuf.decodeFromByteArray<edu.cit.resqmesh.core.network.MeshPayload>(payloadBytes).id }.getOrNull()
                if (transmission != null) privateProgress[message.id] = PrivateTransferProgress(transmission, directedRouteList.size - 1)
                val dispatchResult = when (delivery) {
                    is PrivateDeliveryPlanner.Target.Endpoint -> {
                        if (admitDispatch(message.id)) {
                            networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
                        } else {
                            budgetDeferred = true // stays PENDING until a token refills
                            null
                        }
                    }
                    PrivateDeliveryPlanner.Target.Unavailable -> {
                        null
                    }
                }
                if (dispatchResult?.accepted == true) {
                    messageStore.markSent(message.id)
                    prepaid.remove(message.id)
                    awaitPrivateReceipt(message.id)
                } else {
                    privateProgress.remove(message.id)
                    if (dispatchResult != null) retryRejectedDispatch = true
                }
            } else {
                // Public message retry
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
                    budgetDeferred = true // stays PENDING until a token refills
                    continue
                }
                val result = if (message.isSOS) {
                    networkManager.broadcastPriorityPayload(payloadBytes)
                } else {
                    networkManager.broadcastPayload(payloadBytes)
                }
                if (result.anyAccepted) {
                    messageStore.markSent(message.id)
                    prepaid.remove(message.id)
                } else if (result.neighbors.values.any { it == edu.cit.resqmesh.core.network.TransportDispatchResult.REJECTED_INVALID_FRAME }) {
                    messageStore.expirePending(message.id)
                    prepaid.remove(message.id)
                } else {
                    retryRejectedDispatch = true
                }
            }
        }
        if (retryRejectedDispatch) {
            scheduleOutboxRetry()
        } else if (budgetDeferred) {
            chatBudget.refresh()
            scheduleOutboxRetry(chatBudget.state.value.nextRefillInMs.coerceAtLeast(MIN_BUDGET_RETRY_MS))
        }
    }

    /** Final guard: refuses text above the shared message limit. Logs field and length only, never content. */
    private fun rejectOverLimit(text: String): Boolean {
        if (text.length <= edu.cit.resqmesh.core.security.input.TextField.MESSAGE.maxLength) return false
        AppLogger.d("MeshNetwork", "INPUT_REJECT field=MESSAGE len=${text.length}")
        return true
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false,
        conversationKind: String = "COMMUNITY", channelId: String = "", sosId: String = ""): String {
        require(!isSOS && !isSOSCancel) { "SOS lifecycle uses signed SosRepository operations" }
        require(conversationKind in setOf("COMMUNITY", "RADIO", "SOS"))
        if (rejectOverLimit(text)) return ""
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val payloadBytes = PayloadFactory.buildPublicPayload(
            msgId = messageId,
            timestamp = timestamp,
            senderName = myNodeName,
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel,
            channelId = channelId,
            conversationKind = conversationKind,
            sosId = sosId,
            senderNodeId = networkManager.myNodeId,
            ttl = networkManager.currentMeshTtl()
        )

        val message = ChatMessage(messageId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp,
            conversationKind = conversationKind, channelId = channelId, sosId = sosId, senderNodeId = networkManager.myNodeId)
        prepaid.add(messageId)
        repositoryScope.launch {
            outboxMutex.withLock {
                if (conversationKind == "SOS" && sosRepository?.canReply(sosId) != true) {
                    _publicSendFeedback.emit("This SOS has ended. Its conversation is read-only.")
                    return@withLock
                }
                // Persist first, so acceptance and receipts cannot race an absent database row.
                messageStore.save(message.copy(deliveredTo = listOf("PENDING")), targetName = null)
                val result = if (readyConnectedDevices().isEmpty()) {
                    edu.cit.resqmesh.core.network.BroadcastDispatchResult(emptyMap())
                } else if (isSOS) networkManager.broadcastPriorityPayload(payloadBytes) else networkManager.broadcastPayload(payloadBytes)
                if (result.anyAccepted) {
                    messageStore.markSent(messageId)
                    prepaid.remove(messageId)
                } else if (result.neighbors.values.any { it == edu.cit.resqmesh.core.network.TransportDispatchResult.REJECTED_INVALID_FRAME }) {
                    messageStore.expirePending(messageId)
                    prepaid.remove(messageId)
                } else {
                    scheduleOutboxRetry()
                }
                result.feedback()?.let { _publicSendFeedback.emit(it) }
            }
        }
        return messageId
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null): Boolean {
        if (rejectOverLimit(text)) return false
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
            val message = ChatMessage(
                id = msgId,
                senderName = myNodeName,
                text = text,
                imageBase64 = imageBase64,
                audioBase64 = audioBase64,
                locationLat = locationLat,
                locationLng = locationLng,
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
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
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

        val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
        val isDirect = readyDevices.any { NodeIdentity.matches(it.name, currentTarget) }
        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)

        repositoryScope.launch {
            outboxMutex.withLock {
                // Persist before dispatch so a fast receipt cannot race the local message row.
                messageStore.save(message, targetName = currentTarget)
                messageStore.markPending(msgId)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_PERSISTED message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
                privateProgress[msgId] = PrivateTransferProgress(msgId, directedRouteList.size - 1)
                val dispatchResult = when (delivery) {
                    is PrivateDeliveryPlanner.Target.Endpoint -> {
                        AppLogger.d("MeshNetwork_E2EE", "Private route selected with ${directedRouteList.size - 1} hop(s)")
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
                    privateProgress.remove(msgId)
                    messageStore.markPending(msgId)
                    AppLogger.d("MeshNetwork_E2EE", "Private dispatch was not accepted; retained for directed retry")
                    scheduleOutboxRetry()
                }
            }
        }
        return true
    }
}
