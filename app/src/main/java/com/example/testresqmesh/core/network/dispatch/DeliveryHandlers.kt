package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import android.util.Base64
import com.example.testresqmesh.core.utils.BinaryCompressor
import kotlinx.serialization.ExperimentalSerializationApi

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
                callback.sendPrivateReceipt(updatedPayload)
                return
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

@OptIn(ExperimentalSerializationApi::class)
class StandardMessageHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = !listOf("SYSTEM", "SEEN", "DELIVERED", "GOODBYE", "BLOCK", "UNBLOCK", "LIVE_AUDIO").contains(payloadType)

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = if (payload.isPrivate) payload.targetMessageId.ifBlank { payload.id } else payload.id
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
                        val nextNode = routeIds.getOrNull(myIndex + 1)
                        if (nextNode != null) {
                            val result = callback.forwardPrivatePayload(nextNode, updatedBytes)
                            AppLogger.d("PayloadDispatcher", "Private relay next-hop dispatch: $result")
                            return
                        }
                    }
                }
                AppLogger.d("PayloadDispatcher", "Private relay route unavailable; dropping without broadcast")
            }
        } else {
            // Legacy unscoped SOS cancellation cannot mutate upgraded alerts.
            if (payload.isSOSCancel || payload.isSOS) return

            routePath.add(callback.getMyDeviceName())
            val updatedPayload = payload.copy(routePath = routePath)
            val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)

            callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, payload.locationLat, payload.locationLng, medium, routePath, payload.channelId)
            callback.broadcastPayload(updatedBytes, endpointId)
        }
    }
}
