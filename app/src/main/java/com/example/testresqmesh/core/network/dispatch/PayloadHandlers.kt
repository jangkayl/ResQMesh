package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import android.util.Base64
import com.example.testresqmesh.core.utils.BinaryCompressor
import kotlinx.serialization.ExperimentalSerializationApi

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
        callback.onDeviceNameSync(endpointId, sender)
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "IDENTITY_SYNCED",
            message = "Peer identity confirmed from a SYSTEM pulse",
            peerName = sender,
            endpoint = endpointId
        )
        
        if (payload.publicKey.isNotEmpty()) {
            callback.onPublicKeyReceived(endpointId, sender, payload.publicKey)
        }
        if (payload.connectedNodes.isNotEmpty()) {
            callback.onRoutingTableReceived(sender, payload.connectedNodes)
        }
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "SYSTEM_RECEIVED",
            message = "SYSTEM pulse received; ${payload.connectedNodes.size} advertised neighbor(s)",
            peerName = sender,
            endpoint = endpointId
        )
        
        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList(), payload.channelId)
        
        val routePath = payload.routePath.toMutableList()
        routePath.add(callback.getMyDeviceName())
        val updatedPayload = payload.copy(routePath = routePath)
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
        
        if (directedRoute.isNotEmpty()) {
            val myIndex = directedRoute.indexOf(callback.getMyDeviceName())
            if (myIndex != -1 && myIndex + 1 < directedRoute.size) {
                val nextHopName = directedRoute[myIndex + 1]
                val nextHopEndpointId = callback.getConnectedEndpointIdByName(nextHopName)
                if (nextHopEndpointId != null) {
                    val bytes = ProtoBuf.encodeToByteArray(updatedPayload)
                    callback.sendDirectPayload(nextHopEndpointId, bytes)
                    return
                }
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

class BlockHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val targetName = payload.targetName
        if (targetName == callback.getMyDeviceName()) {
            callback.onDeviceBlocked(payload.senderName)
        } else if (targetName.isNotEmpty()) {
            callback.broadcastPayload(payloadBytes, endpointId)
        }
    }
}

class UnblockHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "UNBLOCK"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val targetName = payload.targetName
        if (targetName == callback.getMyDeviceName()) {
            callback.onDeviceUnblocked(payload.senderName)
        } else if (targetName.isNotEmpty()) {
            callback.broadcastPayload(payloadBytes, endpointId)
        }
    }
}

class LiveAudioHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "LIVE_AUDIO"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val chunk = payload.liveAudioChunk ?: return
        val channelId = payload.channelId
        callback.onLiveAudioChunk(payload.senderName, channelId, chunk)
        
        // STP Directed Routing
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

        if (isEncrypted && targetName == callback.getMyDeviceName()) {
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
            if (targetName == callback.getMyDeviceName()) {
                callback.showNotification(sender, text)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, payload.channelId)
            } else {
                AppLogger.d("PayloadDispatcher", "ROUTE (Relay): Forwarding Private message meant for [$targetName] securely across the mesh.")
                routePath.add(callback.getMyDeviceName())
                val updatedPayload = payload.copy(routePath = routePath)
                val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
                
                if (directedRoute.isNotEmpty()) {
                    val myIndex = directedRoute.indexOf(callback.getMyDeviceName())
                    if (myIndex != -1 && myIndex + 1 < directedRoute.size) {
                        val nextHopName = directedRoute[myIndex + 1]
                        val nextHopEndpointId = callback.getConnectedEndpointIdByName(nextHopName)
                        if (nextHopEndpointId != null) {
                            callback.sendDirectPayload(nextHopEndpointId, updatedBytes)
                            return
                        }
                    }
                }
                
                callback.broadcastPayload(updatedBytes, endpointId)
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
