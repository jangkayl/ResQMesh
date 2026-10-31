package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcherCallback
import edu.cit.resqmesh.core.network.CryptoManager
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.trace.TraceDir
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
import edu.cit.resqmesh.core.logging.trace.TraceScope
import edu.cit.resqmesh.core.logging.trace.TraceStage
import edu.cit.resqmesh.core.network.TransferTraceEmitter
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import android.util.Base64
import edu.cit.resqmesh.core.utils.BinaryCompressor
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
    private companion object {
        const val ATTACHMENT_UNAVAILABLE = "[Attachment unavailable]"
    }

    /** Only audio rejects go to the Voice ring; an image reject is not a voice event. */
    private fun rejectTrace(reason: TraceDropReason, endpointId: String, msgId: String, isAudio: Boolean) {
        if (isAudio) {
            TransferTraceEmitter.voice(TraceDir.IN, TraceStage.DROPPED, TraceScope.UNKNOWN, msgId, reason, endpointId)
        }
    }

    /** Only this phone, as public receiver or private recipient, knows the content is a voice note. */
    private fun voiceNote(msgId: String, scope: TraceScope, endpointId: String) =
        TransferTraceEmitter.voice(TraceDir.IN, TraceStage.RECEIVED, scope, msgId, TraceDropReason.NONE, endpointId)

    // LIVE_AUDIO stays excluded so packets from older builds drop instead of landing as messages.
    override fun canHandle(payloadType: String) = !listOf("SYSTEM", "SEEN", "DELIVERED", "GOODBYE", "BLOCK", "UNBLOCK", "LIVE_AUDIO").contains(payloadType)

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = if (payload.isPrivate) payload.targetMessageId.ifBlank { payload.id } else payload.id
        val targetName = payload.targetName
        val isPrivate = payload.isPrivate
        val isEncrypted = payload.isEncrypted

        var text = payload.text
        var mediaRejected = false
        fun inflateMedia(raw: ByteArray?, isAudio: Boolean): String? {
            if (isPrivate || raw == null) return null
            return when (val r = BinaryCompressor.decompressBounded(raw)) {
                is BinaryCompressor.BoundedResult.Ok -> Base64.encodeToString(r.bytes, Base64.DEFAULT)
                is BinaryCompressor.BoundedResult.TooLarge -> {
                    mediaRejected = true
                    AppLogger.d("PayloadDispatcher", "MEDIA_REJECT reason=too_large bytes=${r.bytes}")
                    rejectTrace(TraceDropReason.MEDIA_REJECT_TOO_LARGE, endpointId, msgId, isAudio)
                    null
                }
                is BinaryCompressor.BoundedResult.Corrupt -> {
                    mediaRejected = true
                    AppLogger.d(
                        "PayloadDispatcher",
                        "MEDIA_REJECT reason=corrupt bytes=${r.bytes} cause=${r.cause}"
                    )
                    rejectTrace(TraceDropReason.MEDIA_REJECT_CORRUPT, endpointId, msgId, isAudio)
                    null
                }
            }
        }
        var imageBase64 = inflateMedia(payload.imageBytes, isAudio = false)
        var audioBase64 = inflateMedia(payload.audioBytes, isAudio = true)
        if (audioBase64 != null) voiceNote(msgId, TraceScope.PUBLIC, endpointId)
        if (mediaRejected) text = if (text.isBlank()) ATTACHMENT_UNAVAILABLE else "$text\n$ATTACHMENT_UNAVAILABLE"
        var locationLat = if (isPrivate) null else payload.locationLat
        var locationLng = if (isPrivate) null else payload.locationLng

        if (isPrivate && !isEncrypted) {
            AppLogger.d("MeshNetwork_E2EE", "Rejected unencrypted private payload from ${AppLogger.redactName(sender)}")
            return
        }

        if (isEncrypted) {
            AppLogger.d("MeshNetwork_E2EE", "INBOUND ENCRYPTED PAYLOAD DETECTED from ${AppLogger.redactName(sender)}")
        } else {
            AppLogger.d(
                "MeshNetwork_E2EE",
                "INBOUND PUBLIC PAYLOAD DETECTED from ${AppLogger.redactName(sender)}. No encryption applied."
            )
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
            AppLogger.d(
                "MeshNetwork_E2EE",
                "E2EE ROUTING: Message is for ${AppLogger.redactName(targetName)}. Acting as a blind encrypted relay."
            )
        }

        val medium = callback.getEndpointMedium(endpointId)
        AppLogger.d(
            "PayloadDispatcher",
            "ROUTE (Received): Message from [${AppLogger.redactName(sender)}] " +
                "arrived physically via endpoint [$endpointId] using $medium"
        )

        val routePath = payload.routePath.toMutableList()
        val directedRoute = payload.directedRoute

        if (isPrivate) {
            if (isTarget) {
                if (audioBase64 != null) voiceNote(msgId, TraceScope.PRIVATE, endpointId)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, payload.channelId)
            } else {
                AppLogger.d(
                    "PayloadDispatcher",
                    "ROUTE (Relay): Forwarding Private message meant for " +
                        "[${AppLogger.redactName(targetName)}] securely across the mesh."
                )
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
