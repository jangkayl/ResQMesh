package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.BlockControlKind
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import android.util.Base64
import com.example.testresqmesh.core.utils.BinaryCompressor
import kotlinx.serialization.ExperimentalSerializationApi

internal const val MAX_PRIVATE_RELAY_HOPS = 3

interface PayloadHandler {
    fun canHandle(payloadType: String): Boolean
    fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback)
}

@OptIn(ExperimentalSerializationApi::class)
class SystemPulseHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "SYSTEM"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = payload.id
        // Direct-link identity binding happens before dispatch in NativeBleManager. A SYSTEM pulse
        // can be forwarded by a different physical peer, so it must never rename this endpoint.
        val senderNodeId = payload.senderNodeId.ifBlank { com.example.testresqmesh.core.model.NodeIdentity.idOf(sender).orEmpty() }
        if (payload.publicKey.isNotEmpty()) {
            callback.onPublicKeyReceived(sender, senderNodeId, payload.publicKey)
        }
        // An empty adjacency is an authoritative withdrawal, not "no update".
        callback.onRoutingTableReceived(
            sender,
            senderNodeId,
            payload.connectedNodes,
            payload.connectedNodeIds,
            payload.topologySequence
        )
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "SYSTEM_RECEIVED",
            message = "SYSTEM pulse received; ${payload.connectedNodes.size} advertised neighbor(s)",
            peerName = sender,
            endpoint = endpointId
        )
        
        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList(), payload.channelId)
        
        val canRelay = if (payload.ttl > 0) {
            payload.ttl > 1
        } else {
            payload.relayHopCount < MAX_SYSTEM_RELAY_HOPS
        }
        if (!canRelay) return
        val routePath = payload.routePath.toMutableList()
        routePath.add(callback.getMyDeviceName())
        val updatedPayload = payload.copy(
            routePath = routePath,
            relayHopCount = payload.relayHopCount + 1,
            ttl = if (payload.ttl > 0) payload.ttl - 1 else 0
        )
        val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
        callback.broadcastPayload(updatedBytes, endpointId)
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "SYSTEM_RELAYED",
            message = "SYSTEM pulse forwarded to eligible direct peers",
            peerName = sender,
            endpoint = endpointId,
            isVerbose = true
        )
    }

    private companion object {
        const val MAX_SYSTEM_RELAY_HOPS = 4
    }
}

@OptIn(ExperimentalSerializationApi::class)
class PingHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "PING" || payloadType == "PONG"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (payload.type == "PONG") {
            callback.onHeartbeatAck(endpointId, payload.targetMessageId)
            AppLogger.event(
                category = TerminalLogCategory.SYNC,
                event = "HEARTBEAT_ACK",
                message = "Heartbeat acknowledgement received",
                peerName = payload.senderName,
                endpoint = endpointId,
                isVerbose = true
            )
            return
        }
        callback.onDeviceNameSync(endpointId, payload.senderName)
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "HEARTBEAT_RECEIVED",
            message = "PING received; replying when it is a heartbeat challenge",
            peerName = payload.senderName,
            endpoint = endpointId,
            isVerbose = true
        )
        if (payload.id.startsWith("HB:")) {
            val reply = MeshPayload(
                id = "HA:${java.util.UUID.randomUUID()}",
                type = "PONG",
                senderName = callback.getMyDeviceName(),
                targetMessageId = payload.id
            )
            callback.sendGattPayload(endpointId, ProtoBuf.encodeToByteArray(reply))
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class ReceiptHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "SEEN" || payloadType == "DELIVERED"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val targetMessageId = payload.targetMessageId
        val reader = payload.reader
        
        val returnRoute = payload.returnRoute.toMutableList()
        val directedRoute = payload.directedRoute.toMutableList()
        
        if (payload.type == "SEEN") {
            callback.onMessageSeen(targetMessageId, reader)
        } else if (payload.type == "DELIVERED") {
            callback.onMessageDelivered(targetMessageId, reader, returnRoute)
        }
        
        returnRoute.add(callback.getMyDeviceName())
        val updatedPayload = payload.copy(returnRoute = returnRoute)
        
        val routeIds = payload.directedRouteNodeIds
        if (payload.isPrivate) {
            val myIndex = routeIds.indexOf(callback.getMyNodeId())
            if (myIndex >= 0) {
                // If this node is the end destination of the receipt return route, do not forward or flood
                if (myIndex == routeIds.lastIndex) {
                    return
                }
                val nextEndpoint = routeIds.getOrNull(myIndex + 1)?.let(callback::getConnectedEndpointIdByNodeId)
                if (nextEndpoint != null) {
                    val result = callback.sendDirectPayload(nextEndpoint, ProtoBuf.encodeToByteArray(updatedPayload))
                    AppLogger.d("PayloadDispatcher", "Private receipt next-hop dispatch: $result")
                    return
                }
            }
            AppLogger.d("PayloadDispatcher", "Private receipt route unavailable; dropping without broadcast")
            return
        }
        if (directedRoute.isNotEmpty()) {
            val myIndex = directedRoute.indexOf(callback.getMyDeviceName())
            val nextHopEndpointId = directedRoute.getOrNull(myIndex + 1)?.let(callback::getConnectedEndpointIdByName)
            if (nextHopEndpointId != null) {
                callback.sendDirectPayload(nextHopEndpointId, ProtoBuf.encodeToByteArray(updatedPayload))
                return
            }
        }
        
        val bytes = ProtoBuf.encodeToByteArray(updatedPayload)
        callback.broadcastPayload(bytes, endpointId)
    }
}

class GoodbyeHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "GOODBYE"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onDeviceGoodbye(endpointId)
        callback.broadcastPayload(payloadBytes, endpointId)
    }
}

private fun forwardBlockControl(endpointId: String, payload: MeshPayload, callback: PayloadDispatcherCallback) {
    val routePath = (payload.routePath + callback.getMyDeviceName()).distinct()
    val forwarded = payload.copy(routePath = routePath)
    val routeIds = forwarded.directedRouteNodeIds
    val index = routeIds.indexOf(callback.getMyNodeId())
    if (index >= 0 && index + 1 < routeIds.size) {
        callback.getConnectedEndpointIdByNodeId(routeIds[index + 1])?.let { nextEndpoint ->
            callback.sendPriorityPayload(nextEndpoint, ProtoBuf.encodeToByteArray(forwarded))
            return
        }
    }
    AppLogger.d("PayloadDispatcher", "Private control route unavailable; not broadcasting")
}

private fun decryptBlockControl(payload: MeshPayload): BlockControlEnvelope? {
    if (!payload.isPrivate || !payload.isEncrypted || payload.encryptedData.isNullOrBlank() || payload.encryptedKey.isNullOrBlank()) return null
    return CryptoManager.decryptHybrid(payload.encryptedData, payload.encryptedKey)
        ?.let(BlockControlEnvelope::decode)
}

class BlockRequestHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK_REQUEST"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (!payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true)) {
            forwardBlockControl(endpointId, payload, callback)
            return
        }
        val envelope = decryptBlockControl(payload) ?: return
        if (envelope.kind != BlockControlKind.REQUEST || envelope.operationId != payload.targetMessageId ||
            !NodeIdentity.matches(envelope.targetName, callback.getMyDeviceName()) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)
        ) return
        callback.onBlockRequest(endpointId, payload, envelope)
    }
}

class BlockAckHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK_ACK"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (!payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true)) {
            forwardBlockControl(endpointId, payload, callback)
            return
        }
        val envelope = decryptBlockControl(payload) ?: return
        if (envelope.kind != BlockControlKind.ACK || envelope.operationId != payload.targetMessageId ||
            !NodeIdentity.matches(envelope.targetName, callback.getMyDeviceName()) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)
        ) return
        callback.onBlockAck(endpointId, payload, envelope)
    }
}

class LegacyBlockControlHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK" || payloadType == "UNBLOCK"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onLegacyBlockControl(payload.type, payload.senderName)
    }
}

class LiveAudioHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "LIVE_AUDIO"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val chunk = payload.liveAudioChunk ?: return
        val channelId = payload.channelId
        callback.onLiveAudioChunk(payload.senderName, channelId, chunk)
        val stpNeighbors = callback.getStpNeighbors()
        if (stpNeighbors.isEmpty()) return

        for (neighborName in stpNeighbors) {
            val neighborEndpointId = callback.getConnectedEndpointIdByName(neighborName)
            if (neighborEndpointId != null && neighborEndpointId != endpointId) {
                callback.sendDirectPayload(neighborEndpointId, payloadBytes)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class StandardMessageHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = !listOf("SYSTEM", "SEEN", "DELIVERED", "GOODBYE", "BLOCK", "UNBLOCK", "LIVE_AUDIO").contains(payloadType)
    
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = payload.id
        val targetName = payload.targetName
        val isPrivate = payload.isPrivate
        val isEncrypted = payload.isEncrypted

        var text = payload.text
        var imageBase64 = if (isPrivate) null else payload.imageBytes?.let { Base64.encodeToString(BinaryCompressor.decompress(it), Base64.DEFAULT) }
        var audioBase64 = if (isPrivate) null else payload.audioBytes?.let { Base64.encodeToString(BinaryCompressor.decompress(it), Base64.DEFAULT) }
        var locationLat = if (isPrivate) null else payload.locationLat
        var locationLng = if (isPrivate) null else payload.locationLng

        if (isPrivate && !isEncrypted) {
            AppLogger.d("MeshNetwork_E2EE", "Rejected unencrypted private payload from $sender")
            return
        }
        
        if (isEncrypted) {
            AppLogger.d("MeshNetwork_E2EE", "INBOUND ENCRYPTED PAYLOAD DETECTED from $sender")
        } else {
            AppLogger.d("MeshNetwork_E2EE", "INBOUND PUBLIC PAYLOAD DETECTED from $sender. No encryption applied.")
        }

        val isTarget = payload.targetNodeId.isNotBlank() && payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true) ||
            (payload.targetNodeId.isBlank() && NodeIdentity.matches(targetName, callback.getMyDeviceName()))
        if (isEncrypted && isTarget) {
            val encryptedData = payload.encryptedData
            val encryptedKey = payload.encryptedKey
            
            AppLogger.d("MeshNetwork_E2EE", "Message is addressed to ME. Attempting RSA+AES Hybrid Decryption...")
            if (encryptedData.isNullOrBlank() || encryptedKey.isNullOrBlank()) {
                AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Missing encrypted envelope")
                return
            }
            val decryptedJsonString = CryptoManager.decryptHybrid(encryptedData, encryptedKey)
            if (decryptedJsonString == null) {
                AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Could not decrypt payload")
                return
            }
            try {
                val innerPayload = org.json.JSONObject(decryptedJsonString)
                text = innerPayload.getString("text")
                if (innerPayload.has("image")) imageBase64 = innerPayload.getString("image")
                if (innerPayload.has("audio")) audioBase64 = innerPayload.getString("audio")
                if (innerPayload.has("locationLat")) locationLat = innerPayload.getDouble("locationLat")
                if (innerPayload.has("locationLng")) locationLng = innerPayload.getDouble("locationLng")
                AppLogger.d("MeshNetwork_E2EE", "E2EE SUCCESS: Decrypted private payload")
            } catch (e: Exception) {
                AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Invalid encrypted content")
                return
            }
        } else if (isEncrypted) {
            text = "[ENCRYPTED CONTENT: Routing...]"
            imageBase64 = null
            audioBase64 = null
            AppLogger.d("MeshNetwork_E2EE", "E2EE ROUTING: Message is for $targetName. Acting as a blind encrypted relay.")
        }

        val medium = callback.getEndpointMedium(endpointId)
        AppLogger.d("PayloadDispatcher", "ROUTE (Received): Message from [$sender] arrived physically via endpoint [$endpointId] using $medium")

        val routePath = payload.routePath.toMutableList()
        val directedRoute = payload.directedRoute

        if (isPrivate) {
            if (isTarget) {
                callback.showNotification(sender, text)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, payload.channelId)
            } else {
                AppLogger.d("PayloadDispatcher", "ROUTE (Relay): Forwarding Private message meant for [$targetName] securely across the mesh.")
                routePath.add(callback.getMyDeviceName())
                val updatedPayload = payload.copy(routePath = routePath)
                val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
                
                val routeIds = payload.directedRouteNodeIds
                if (routeIds.isNotEmpty()) {
                    val myIndex = routeIds.indexOf(callback.getMyNodeId())
                    if (myIndex >= 0) {
                        val nextEndpoint = routeIds.getOrNull(myIndex + 1)?.let(callback::getConnectedEndpointIdByNodeId)
                        if (nextEndpoint != null) {
                            val result = callback.sendDirectPayload(nextEndpoint, updatedBytes)
                            AppLogger.d("PayloadDispatcher", "Private relay next-hop dispatch: $result")
                            return
                        }
                    }
                }
                AppLogger.d("PayloadDispatcher", "Private relay route unavailable; dropping without broadcast")
            }
        } else {
            if (payload.isSOSCancel) {
                callback.onSosCancelled()
            }
            if (payload.isSOS && sender != callback.getMyDeviceName()) {
                callback.showSosEmergencyNotification(sender, text)
            }
            
            routePath.add(callback.getMyDeviceName())
            val updatedPayload = payload.copy(routePath = routePath)
            val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
            
            callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, payload.locationLat, payload.locationLng, medium, routePath, payload.channelId)
            callback.broadcastPayload(updatedBytes, endpointId)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class DomainEventHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "DOMAIN_EVENT"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        // IncidentRepository validates and only then relays accepted business events.  Relaying
        // here would spread malformed or unauthorized state changes before policy can reject them.
        callback.onDomainEvent(endpointId, payload)
    }
}

class EventSyncRequestHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "EVENT_SYNC_REQ"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onEventSyncRequest(endpointId, payload)
    }
}

class EventSyncResponseHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "EVENT_SYNC_RESP"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onEventSyncResponse(endpointId, payload)
    }
}
