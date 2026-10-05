package com.example.testresqmesh.data.repository.mesh

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import android.util.Base64
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.testresqmesh.data.repository.MeshRouter
import com.example.testresqmesh.data.repository.MessageStore
import com.example.testresqmesh.data.repository.PeerPublicKeyDirectory
import com.example.testresqmesh.data.repository.PrivateDeliveryPlanner
import com.example.testresqmesh.data.repository.PrivateMessageInsert

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
                    repositoryScope.launch {
                        val inserted = messageStore.saveIncomingPrivateIfAbsent(message)
                        if (inserted == PrivateMessageInsert.REJECTED) return@launch
                        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_STORED message=$msgId insertion=$inserted")
                        val reversedRoute = routePath.reversed().toMutableList()
                        if (reversedRoute.isEmpty()) reversedRoute.add(sender)
                        if (!NodeIdentity.matches(reversedRoute.first(), myNodeName)) reversedRoute.add(0, myNodeName)
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute)
                        if (inserted == PrivateMessageInsert.DUPLICATE && messageStore.incomingPrivateWasSeen(msgId)) {
                            networkManager.broadcastSeenReceipt(msgId, true, endpointId, reversedRoute)
                        }
                        if (inserted == PrivateMessageInsert.INSERTED) networkManager.showPrivateMessageNotification(sender, text)
                    }
                } else {
                    repositoryScope.launch {
                        if (!messageStore.contains(msgId)) messageStore.save(message, targetName = null)
                        networkManager.markPayloadStored(msgId)
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
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

    private suspend fun receiveConversation(endpoint: String, p: com.example.testresqmesh.core.network.MeshPayload) {
        if (p.senderNodeId.isBlank() || p.senderNodeId == networkManager.myNodeId || p.id.length !in 1..100 ||
            p.senderName.length !in 1..160 || p.text.length > 8192 || p.isPrivate || p.isEncrypted ||
            p.conversationKind !in setOf("COMMUNITY", "RADIO", "SOS") || p.ttl !in 1..10 || p.relayHopCount !in 0..10) return
        if (p.conversationKind == "RADIO" && p.channelId !in (1..5).map { it.toString() }) return
        if (p.conversationKind == "SOS" && (p.audioBytes != null || p.imageBytes != null || sosRepository?.canReply(p.sosId) != true)) return
        if ((p.audioBytes?.size ?: 0) > 2 * 1024 * 1024 || (p.imageBytes?.size ?: 0) > 2 * 1024 * 1024) return
        val attachments = runCatching {
            val audio = p.audioBytes?.let { Base64.encodeToString(com.example.testresqmesh.core.utils.BinaryCompressor.decompress(it, 2 * 1024 * 1024), Base64.NO_WRAP) }
            val image = p.imageBytes?.let { Base64.encodeToString(com.example.testresqmesh.core.utils.BinaryCompressor.decompress(it, 2 * 1024 * 1024), Base64.NO_WRAP) }
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
        networkManager.broadcastDeliveredReceipt(p.id, isPrivate = false)
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
