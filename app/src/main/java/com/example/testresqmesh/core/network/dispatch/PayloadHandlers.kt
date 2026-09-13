package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.utils.AppLogger
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
        
        if (payload.publicKey.isNotEmpty()) {
            callback.onPublicKeyReceived(sender, payload.publicKey)
        }
        if (payload.connectedNodes.isNotEmpty()) {
            callback.onRoutingTableReceived(sender, payload.connectedNodes)
        }
        
        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList(), payload.channelId)
        
        val routePath = payload.routePath.toMutableList()
        routePath.add(callback.getMyDeviceName())
        val updatedPayload = payload.copy(routePath = routePath)
        val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
        callback.broadcastPayload(updatedBytes, endpointId)
    }
}

@OptIn(ExperimentalSerializationApi::class)
class PingHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "PING"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        callback.onDeviceNameSync(endpointId, sender)
        
        // PINGs exist purely to keep GATT sockets alive and refresh the Zombie Watchdog timer.
        // We do NOT broadcast them to the rest of the Mesh, saving massive battery power.
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
        var imageBase64 = payload.imageBytes?.let { Base64.encodeToString(BinaryCompressor.decompress(it), Base64.DEFAULT) }
        var audioBase64 = payload.audioBytes?.let { Base64.encodeToString(BinaryCompressor.decompress(it), Base64.DEFAULT) }
        
        if (isEncrypted) {
            val previewData = payload.encryptedData?.take(20) ?: "MISSING"
            AppLogger.d("MeshNetwork_E2EE", "INBOUND ENCRYPTED PAYLOAD DETECTED from $sender. Raw Ciphertext preview: [$previewData...]")
        } else {
            AppLogger.d("MeshNetwork_E2EE", "INBOUND PUBLIC PAYLOAD DETECTED from $sender. No encryption applied.")
        }

        if (isEncrypted && targetName == callback.getMyDeviceName()) {
            val encryptedData = payload.encryptedData
            val encryptedKey = payload.encryptedKey
            
            AppLogger.d("MeshNetwork_E2EE", "Message is addressed to ME. Attempting RSA+AES Hybrid Decryption...")
            if (encryptedData != null && encryptedKey != null) {
                val decryptedJsonString = CryptoManager.decryptHybrid(encryptedData, encryptedKey)
                if (decryptedJsonString != null) {
                    try {
                        val innerPayload = org.json.JSONObject(decryptedJsonString)
                        text = innerPayload.optString("text", text)
                        if (innerPayload.has("image")) imageBase64 = innerPayload.getString("image")
                        if (innerPayload.has("audio")) audioBase64 = innerPayload.getString("audio")
                        AppLogger.d("MeshNetwork_E2EE", "E2EE SUCCESS: Decrypted private payload! Plaintext: [$text]")
                    } catch (e: Exception) {
                        text = "[ENCRYPTED CONTENT: Inner JSON Parse Failed]"
                    }
                } else {
                    text = "[ENCRYPTED CONTENT: Decryption Failed]"
                    AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Could not decrypt payload. Keys do not match.")
                }
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
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, payload.locationLat, payload.locationLng, medium, routePath, payload.channelId)
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
