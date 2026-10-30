package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.dispatch.InboundPayloadValidator.Companion.MAX_TTL
import edu.cit.resqmesh.core.network.ratelimit.ReceiptReplayLimiter
import edu.cit.resqmesh.core.logging.AppLogger
import android.util.Base64
import edu.cit.resqmesh.core.utils.BinaryCompressor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import edu.cit.resqmesh.data.repository.MeshRouter
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import edu.cit.resqmesh.data.repository.PrivateDeliveryPlanner
import edu.cit.resqmesh.data.repository.PrivateMessageInsert

private const val MAX_MESSAGE_ID_LENGTH = 100
private const val MAX_SENDER_NAME_LENGTH = 160
private const val MAX_CONVERSATION_TEXT_LENGTH = 8192
private val RADIO_CHANNEL_IDS = (1..5).map { it.toString() }

/** A decoded chat frame as the transport hands it to the repository. */
internal data class InboundChatPayload(
    val endpointId: String,
    val msgId: String,
    val sender: String,
    val text: String,
    val isPrivate: Boolean,
    val isSystem: Boolean,
    val imageBase64: String?,
    val audioBase64: String?,
    val locationLat: Double?,
    val locationLng: Double?,
    val medium: String,
    val routePath: List<String>,
    val channelId: String
) {
    fun toChatMessage(isDirect: Boolean) = ChatMessage(
        id = msgId,
        senderName = sender,
        text = text,
        imageBase64 = imageBase64,
        audioBase64 = audioBase64,
        locationLat = locationLat,
        locationLng = locationLng,
        isMine = false,
        isPrivate = isPrivate,
        timestamp = System.currentTimeMillis(),
        isHopped = !isDirect,
        receiveMedium = medium,
        outboundRoute = routePath,
        isSOS = false,
        conversationKind = if (isPrivate) "PRIVATE" else if (audioBase64 != null) "LEGACY_RADIO" else "COMMUNITY",
        senderNodeId = NodeIdentity.idOf(sender).orEmpty()
    )
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class MeshInboundMessages(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope,
    private val sosRepository: SosReplyCapability?,
    private val meshRouter: MeshRouter,
    private val _currentChannelId: MutableStateFlow<String>,
    private val _connectedDevices: MutableStateFlow<List<ConnectedDevice>>,
    private val _incomingSosAlert: MutableStateFlow<ChatMessage?>,
    private val incomingVoiceMessage: kotlinx.coroutines.flow.MutableSharedFlow<ChatMessage>,
    private val localName: () -> String,
    private val readyPeers: () -> List<ConnectedDevice>,
    private val observeName: (String, Boolean) -> Unit,
    private val outboxMutex: Mutex,
    clock: () -> Long = System::currentTimeMillis
) {
    private val receiptReplays = ReceiptReplayLimiter(clock = clock)
    private val myNodeName get() = localName()
    private fun readyConnectedDevices() = readyPeers()
    private fun recordPeerName(name: String, fromReadyLink: Boolean = false) = observeName(name, fromReadyLink)



    fun onNetworkConversationMessage(endpoint: String, payload: MeshPayload): Unit = run {
        repositoryScope.launch { receiveConversation(endpoint, payload) }
    }

    fun onNetworkMessageReceived(inbound: InboundChatPayload) {
        val sender = inbound.sender
        val isPublicContent = !inbound.isSystem && !inbound.isPrivate
        if (sender == myNodeName) {
            // Our own broadcast row already exists when a duplicate returns by another path.
            if (isPublicContent) networkManager.markPayloadStored(inbound.msgId)
            return
        }
        if (!inbound.isSystem) recordPeerName(sender)
        meshRouter.markNodeSeen(sender)
        meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
        if (inbound.isSystem) return
        if (!inbound.isPrivate && inbound.channelId != _currentChannelId.value) {
            // Legacy off-channel content is intentionally ignored locally. Onward custody
            // still has to commit before the journal can release it.
            networkManager.markPayloadStored(inbound.msgId)
            return
        }
        val message = inbound.toChatMessage(isDirect = _connectedDevices.value.any { NodeIdentity.matches(it.name, sender) })
        if (inbound.isPrivate) {
            repositoryScope.launch { receivePrivate(inbound.endpointId, message, inbound.routePath) }
        } else {
            repositoryScope.launch { storePublicMessage(message) }
            if (message.isSOS) {
                _incomingSosAlert.value = message
            }
        }

        if (message.conversationKind == "RADIO" && message.audioBase64 != null) {
            incomingVoiceMessage.tryEmit(message)
        }
    }

    private suspend fun storePublicMessage(message: ChatMessage) {
        val duplicate = messageStore.contains(message.id)
        if (!duplicate) messageStore.save(message, targetName = null)
        networkManager.markPayloadStored(message.id)
        if (!duplicate || receiptReplays.allow(message.senderName, message.id)) {
            networkManager.broadcastDeliveredReceipt(message.id, isPrivate = false)
        }
    }

    private suspend fun receivePrivate(endpointId: String, message: ChatMessage, routePath: List<String>) {
        val msgId = message.id
        val sender = message.senderName
        val inserted = messageStore.saveIncomingPrivateIfAbsent(message)
        if (inserted == PrivateMessageInsert.REJECTED) return
        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_STORED message=$msgId insertion=$inserted")
        val reversedRoute = routePath.reversed().toMutableList()
        if (reversedRoute.isEmpty()) reversedRoute.add(sender)
        if (!NodeIdentity.matches(reversedRoute.first(), myNodeName)) reversedRoute.add(0, myNodeName)
        val duplicate = inserted == PrivateMessageInsert.DUPLICATE
        if (!duplicate || receiptReplays.allow(sender, msgId)) {
            networkManager.broadcastDeliveredReceipt(
                msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute
            )
        }
        val replaySeen = duplicate && messageStore.incomingPrivateWasSeen(msgId)
        if (replaySeen && receiptReplays.allow(sender, "seen:$msgId")) {
            networkManager.broadcastSeenReceipt(msgId, true, endpointId, reversedRoute)
        }
        if (inserted == PrivateMessageInsert.INSERTED) {
            networkManager.showPrivateMessageNotification(sender, message.text)
        }
    }

    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null) {
        repositoryScope.launch {
            messageStore.markSeen(messageId, "Me")
        }

        if (isPrivate && targetName != null) {
            val readyDevices = readyConnectedDevices()
            val route = meshRouter.findShortestPath(myNodeName, targetName, readyDevices)
            val delivery = PrivateDeliveryPlanner.select(targetName, route, readyDevices)
            if (delivery is PrivateDeliveryPlanner.Target.Endpoint) {
                networkManager.broadcastSeenReceipt(messageId, true, delivery.endpointId, route)
            } else {
                AppLogger.d("MeshNetwork_E2EE", "Private seen receipt route unavailable; not broadcasting")
            }
        } else {
            networkManager.broadcastSeenReceipt(messageId, false, null)
        }
    }

    private suspend fun receiveConversation(endpoint: String, p: MeshPayload) {
        if (!isAcceptableConversation(p)) return
        val attachments = runCatching {
            val audio = p.audioBytes?.let { inflateToBase64(it) }
            val image = p.imageBytes?.let { inflateToBase64(it) }
            audio to image
        }.getOrElse {
            AppLogger.d("CONVERSATION", "Rejected invalid attachment")
            return
        }
        val message = storeIfNew(p, attachments.first, attachments.second)
        val fresh = message != null
        if (fresh || receiptReplays.allow(p.senderNodeId, p.id)) {
            networkManager.broadcastDeliveredReceipt(p.id, isPrivate = false)
        }
        if (!fresh && !networkManager.hasPendingCustody(p.id)) return
        if (fresh) recordPeerName(p.senderName)
        message?.takeIf { it.conversationKind == "RADIO" && it.audioBase64 != null }?.let { incomingVoiceMessage.tryEmit(it) }
        if (p.ttl > 1 && p.relayHopCount < MAX_TTL) {
            networkManager.broadcastPayload(ProtoBuf.encodeToByteArray(p.copy(ttl = p.ttl - 1,
                relayHopCount = p.relayHopCount + 1, routePath = p.routePath + myNodeName)), endpoint)
        }
        networkManager.markPayloadStored(p.id)
    }

    private suspend fun isAcceptableConversation(p: MeshPayload): Boolean {
        if (p.senderNodeId.isBlank() || p.senderNodeId == networkManager.myNodeId || p.id.length !in 1..MAX_MESSAGE_ID_LENGTH ||
            p.senderName.length !in 1..MAX_SENDER_NAME_LENGTH || p.text.length > MAX_CONVERSATION_TEXT_LENGTH || p.isPrivate || p.isEncrypted ||
            p.conversationKind !in setOf("COMMUNITY", "RADIO", "SOS") || p.ttl !in 1..MAX_TTL || p.relayHopCount !in 0..MAX_TTL) return false
        if (p.conversationKind == "RADIO" && p.channelId !in RADIO_CHANNEL_IDS) return false
        val sosBlocked = p.audioBytes != null || p.imageBytes != null || sosRepository?.canReply(p.sosId) != true
        if (p.conversationKind == "SOS" && sosBlocked) return false
        val maxMedia = BinaryCompressor.MAX_MEDIA_BYTES
        return (p.audioBytes?.size ?: 0) <= maxMedia && (p.imageBytes?.size ?: 0) <= maxMedia
    }

    /** The stored message, or null when this conversation message was already known. */
    private suspend fun storeIfNew(p: MeshPayload, audioBase64: String?, imageBase64: String?): ChatMessage? =
        outboxMutex.withLock {
            if (messageStore.contains(p.id)) return@withLock null
            val message = ChatMessage(p.id, p.senderName, p.text, imageBase64, audioBase64, p.locationLat, p.locationLng,
                false, timestamp = System.currentTimeMillis(), isHopped = p.relayHopCount > 0,
                outboundRoute = p.routePath, conversationKind = p.conversationKind, channelId = p.channelId,
                sosId = p.sosId, senderNodeId = p.senderNodeId)
            messageStore.save(message, null)
            message
        }

    fun deleteConversationWith(peerName: String) {
        repositoryScope.launch {
            messageStore.deleteConversation(peerName)
        }
    }

    fun hasPendingPublicKeyChange(peerName: String): Boolean = publicKeys.hasPendingChange(peerName)

    fun acceptPendingPublicKeyChange(peerName: String): Boolean = publicKeys.acceptPendingChange(peerName)

    fun rejectPendingPublicKeyChange(peerName: String): Boolean = publicKeys.rejectPendingChange(peerName)
}

private fun inflateToBase64(bytes: ByteArray): String =
    Base64.encodeToString(BinaryCompressor.decompress(bytes, BinaryCompressor.MAX_MEDIA_BYTES), Base64.NO_WRAP)
