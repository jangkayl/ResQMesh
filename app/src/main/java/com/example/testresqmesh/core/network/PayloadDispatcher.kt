package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

class PayloadDispatcher(private val callback: PayloadDispatcherCallback) {

    fun dispatch(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            
            // 1. DUPLICATE MESSAGE CHECK (THE BOUNCER)
            if (payload.id.isEmpty()) return
            val msgId = payload.id
            val seenMessageIds = callback.getSeenMessageIds()

            if (seenMessageIds.contains(msgId)) return
            seenMessageIds.add(msgId)
            if (seenMessageIds.size > 500) {
                val iterator = seenMessageIds.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }

            val sender = payload.senderName
            if (sender == callback.getMyDeviceName()) return

            // 2. ROUTING LOGIC
            when (payload.type) {
                "SYSTEM" -> handleSystemPulse(endpointId, msgId, sender, payloadBytes, payload)
                "SEEN", "DELIVERED" -> handleReceipt(endpointId, payload.type, msgId, sender, payloadBytes, payload)
                "GOODBYE" -> handleGoodbye(endpointId, sender, payloadBytes)
                else -> handleStandardMessage(endpointId, msgId, sender, payloadBytes, payload)
            }
        } catch (e: Exception) {
            AppLogger.d("PAYLOAD_DISPATCHER", "Error parsing Protobuf payload: ${e.message}")
        }
    }

    private fun handleGoodbye(endpointId: String, sender: String, payloadBytes: ByteArray) {
        callback.onDeviceGoodbye(endpointId)
        callback.broadcastPayload(payloadBytes, endpointId)
    }

    private fun handleReceipt(endpointId: String, payloadType: String, msgId: String, sender: String, payloadBytes: ByteArray, payload: MeshPayload) {
        val targetMessageId = payload.targetMessageId
        val reader = payload.reader
        
        val returnRoute = payload.returnRoute.toMutableList()
        val directedRoute = payload.directedRoute.toMutableList()
        
        if (payloadType == "SEEN") {
            callback.onMessageSeen(targetMessageId, reader)
        } else if (payloadType == "DELIVERED") {
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

    private fun handleSystemPulse(endpointId: String, msgId: String, sender: String, payloadBytes: ByteArray, payload: MeshPayload) {
        callback.onDeviceNameSync(endpointId, sender)
        
        if (payload.publicKey.isNotEmpty()) {
            callback.onPublicKeyReceived(sender, payload.publicKey)
        }
        if (payload.connectedNodes.isNotEmpty()) {
            callback.onRoutingTableReceived(sender, payload.connectedNodes)
        }
        
        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList())
        callback.broadcastPayload(payloadBytes, endpointId)
    }

    private fun handleStandardMessage(endpointId: String, msgId: String, sender: String, payloadBytes: ByteArray, payload: MeshPayload) {
        val targetName = payload.targetName
        val isPrivate = payload.isPrivate
        val isEncrypted = payload.isEncrypted

        var text = payload.text
        var imageBase64 = payload.image
        var audioBase64 = payload.audio
        
        // PROOF OF ENCRYPTION LOGGING
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
            AppLogger.d("MeshNetwork_E2EE", "E2EE ROUTING: Message is for $targetName. I cannot read it. Acting as a blind encrypted relay.")
        }

        val medium = callback.getEndpointMedium(endpointId)
        AppLogger.d("PayloadDispatcher", "ROUTE (Received): Message from [$sender] arrived physically via endpoint [$endpointId] using $medium")

        val routePath = payload.routePath.toMutableList()
        val directedRoute = payload.directedRoute

        if (isPrivate) {
            if (targetName == callback.getMyDeviceName()) {
                callback.showNotification(sender, text)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, payload.locationLat, payload.locationLng, medium, routePath)
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
            
            callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, payload.locationLat, payload.locationLng, medium, routePath)
            callback.broadcastPayload(updatedBytes, endpointId)
        }
    }
}
