package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.MeshNetworkGateway
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

@Suppress("TooManyFunctions")
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
    private val outboxMutex: Mutex
) {
    private val receiptReplays = ReceiptReplayLimiter()
    private val myNodeName get() = localName()
    private fun readyConnectedDevices() = readyPeers()
    private fun recordPeerName(name: String, fromReadyLink: Boolean = false) = observeName(name, fromReadyLink)



    fun onNetworkConversationMessage(endpoint: String, payload: MeshPayload): Unit = run {
        repositoryScope.launch { receiveConversation(endpoint, payload) }
    }

    fun onNetworkMessageReceived(endpointId: String, msgId: String, sender: String, text: String, isPrivate: Boolean, isSystem: Boolean, img: String?, audio: String?, lat: Double?, lng: Double?, medium: String, routePath: List<String>, channelId: String): Unit = run {
        if (sender != myNodeName) {
            if (!isSystem) recordPeerName(sender)
            meshRouter.markNodeSeen(sender)
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())

            if (!isSystem && (isPrivate || channelId == _currentChannelId.value)) {
                val isDirect = _connectedDevices.value.any { NodeIdentity.matches(it.name, sender) }
                val message = ChatMessage(
                    id = msgId,
                    senderName = sender,
                    text = text,
                    imageBase64 = img,
                    audioBase64 = audio,
                    locationLat = lat,
                    locationLng = lng,
                    isMine = false,
                    isPrivate = isPrivate,
                    timestamp = System.currentTimeMillis(),
                    isHopped = !isDirect,
                    receiveMedium = medium,
                    outboundRoute = routePath,
                    isSOS = false,
                    conversationKind = if (isPrivate) "PRIVATE" else if (audio != null) "LEGACY_RADIO" else "COMMUNITY",
                    senderNodeId = NodeIdentity.idOf(sender).orEmpty()
                )
                if (isPrivate) {
                    repositoryScope.launch { receivePrivate(endpointId, message, routePath) }
                } else {
                    repositoryScope.launch {
                        val duplicate = messageStore.contains(msgId)
                        if (!duplicate) messageStore.save(message, targetName = null)
                        networkManager.markPayloadStored(msgId)
                        if (!duplicate || receiptReplays.allow(sender, msgId)) {
                            networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
                        }
                    }

                    if (message.isSOS) {
                        _incomingSosAlert.value = message
                    }
                }

                if (message.conversationKind == "RADIO" && message.audioBase64 != null) {
                    incomingVoiceMessage.tryEmit(message)
                }
            } else if (!isSystem && !isPrivate) {
                // Legacy off-channel content is intentionally ignored locally. Onward custody
                // still has to commit before the journal can release it.
                networkManager.markPayloadStored(msgId)
            }
        } else if (!isSystem && !isPrivate) {
            // Our own broadcast row already exists when a duplicate returns by another path.
            networkManager.markPayloadStored(msgId)
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

    private suspend fun receiveConversation(endpoint: String, p: edu.cit.resqmesh.core.network.MeshPayload) {
        if (p.senderNodeId.isBlank() || p.senderNodeId == networkManager.myNodeId || p.id.length !in 1..100 ||
            p.senderName.length !in 1..160 || p.text.length > 8192 || p.isPrivate || p.isEncrypted ||
            p.conversationKind !in setOf("COMMUNITY", "RADIO", "SOS") || p.ttl !in 1..10 || p.relayHopCount !in 0..10) return
        if (p.conversationKind == "RADIO" && p.channelId !in (1..5).map { it.toString() }) return
        val sosBlocked = p.audioBytes != null || p.imageBytes != null || sosRepository?.canReply(p.sosId) != true
        if (p.conversationKind == "SOS" && sosBlocked) return
        val maxMedia = BinaryCompressor.MAX_MEDIA_BYTES
        if ((p.audioBytes?.size ?: 0) > maxMedia || (p.imageBytes?.size ?: 0) > maxMedia) return
        val attachments = runCatching {
            val audio = p.audioBytes?.let { inflateToBase64(it) }
            val image = p.imageBytes?.let { inflateToBase64(it) }
            audio to image
        }.getOrElse {
            AppLogger.d("CONVERSATION", "Rejected invalid attachment")
            return
        }
        var fresh = false
        var message: ChatMessage? = null
        outboxMutex.withLock {
            if (!messageStore.contains(p.id)) {
                message = ChatMessage(p.id, p.senderName, p.text, attachments.second, attachments.first, p.locationLat, p.locationLng,
                    false, timestamp = System.currentTimeMillis(), isHopped = p.relayHopCount > 0,
                    outboundRoute = p.routePath, conversationKind = p.conversationKind, channelId = p.channelId,
                    sosId = p.sosId, senderNodeId = p.senderNodeId)
                messageStore.save(message!!, null)
                fresh = true
            }
        }
        if (fresh || receiptReplays.allow(p.senderNodeId, p.id)) {
            networkManager.broadcastDeliveredReceipt(p.id, isPrivate = false)
        }
        if (!fresh && !networkManager.hasPendingCustody(p.id)) return
        if (fresh) recordPeerName(p.senderName)
        if (fresh) message?.takeIf { it.conversationKind == "RADIO" && it.audioBase64 != null }?.let { incomingVoiceMessage.tryEmit(it) }
        if (p.ttl > 1 && p.relayHopCount < 10) {
            networkManager.broadcastPayload(ProtoBuf.encodeToByteArray(p.copy(ttl = p.ttl - 1,
                relayHopCount = p.relayHopCount + 1, routePath = p.routePath + myNodeName)), endpoint)
        }
        networkManager.markPayloadStored(p.id)
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
