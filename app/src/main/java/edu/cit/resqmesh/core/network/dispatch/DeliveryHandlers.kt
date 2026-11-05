package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.PayloadTypes
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
    override fun canHandle(payloadType: String) =
        payloadType == PayloadTypes.SEEN || payloadType == PayloadTypes.DELIVERED

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val targetMessageId = payload.targetMessageId
        val reader = payload.reader

        val returnRoute = payload.returnRoute.toMutableList()
        val directedRoute = payload.directedRoute.toMutableList()

        if (payload.type == PayloadTypes.SEEN) {
            callback.onMessageSeen(targetMessageId, reader)
        } else if (payload.type == PayloadTypes.DELIVERED) {
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
    override fun canHandle(payloadType: String) = !listOf(
        PayloadTypes.SYSTEM, PayloadTypes.SEEN, PayloadTypes.DELIVERED, PayloadTypes.GOODBYE,
        PayloadTypes.BLOCK, PayloadTypes.UNBLOCK, PayloadTypes.LIVE_AUDIO
    ).contains(payloadType)

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = if (payload.isPrivate) payload.targetMessageId.ifBlank { payload.id } else payload.id
        val targetName = payload.targetName
        val isPrivate = payload.isPrivate
        val isEncrypted = payload.isEncrypted

        val media = inflatePublicMedia(payload, endpointId, msgId)
        var imageBase64 = media.imageBase64
        var audioBase64 = media.audioBase64
        if (audioBase64 != null) voiceNote(msgId, TraceScope.PUBLIC, endpointId)
        var text = if (media.rejected) withAttachmentUnavailableMarker(payload.text) else payload.text
        var locationLat = if (isPrivate) null else payload.locationLat
        var locationLng = if (isPrivate) null else payload.locationLng

        if (isPrivate && !isEncrypted) {
            AppLogger.d("MeshNetwork_E2EE", "Rejected unencrypted private payload from ${AppLogger.redactName(sender)}")
            return
        }
        logInboundProtection(isEncrypted, sender)

        val isTarget = isAddressedToMe(payload, callback)
        if (isEncrypted && isTarget) {
            val content = decryptPrivateContent(payload) ?: return
            text = content.text
            imageBase64 = content.imageBase64 ?: imageBase64
            audioBase64 = content.audioBase64 ?: audioBase64
            locationLat = content.locationLat ?: locationLat
            locationLng = content.locationLng ?: locationLng
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
        when {
            isPrivate && isTarget -> {
                if (audioBase64 != null) voiceNote(msgId, TraceScope.PRIVATE, endpointId)
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64,
                    locationLat, locationLng, medium, routePath, payload.channelId)
            }
            isPrivate -> relayPrivate(payload, routePath, callback)
            // Legacy unscoped SOS cancellation cannot mutate upgraded alerts.
            payload.isSOSCancel || payload.isSOS -> return
            else -> {
                routePath.add(callback.getMyDeviceName())
                val updatedBytes = ProtoBuf.encodeToByteArray(payload.copy(routePath = routePath))
                callback.onMessageReceived(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64,
                    payload.locationLat, payload.locationLng, medium, routePath, payload.channelId)
                callback.broadcastPayload(updatedBytes, endpointId)
            }
        }
    }

    /** Public media after bounded inflation; private media only travels inside the encrypted envelope. */
    private class InboundMedia(val imageBase64: String?, val audioBase64: String?, val rejected: Boolean)

    private fun inflatePublicMedia(payload: MeshPayload, endpointId: String, msgId: String): InboundMedia {
        var rejected = false
        fun inflate(raw: ByteArray?, isAudio: Boolean): String? {
            if (payload.isPrivate || raw == null) return null
            return when (val r = BinaryCompressor.decompressBounded(raw)) {
                is BinaryCompressor.BoundedResult.Ok -> Base64.encodeToString(r.bytes, Base64.DEFAULT)
                is BinaryCompressor.BoundedResult.TooLarge -> {
                    rejected = true
                    AppLogger.d("PayloadDispatcher", "MEDIA_REJECT reason=too_large bytes=${r.bytes}")
                    rejectTrace(TraceDropReason.MEDIA_REJECT_TOO_LARGE, endpointId, msgId, isAudio)
                    null
                }
                is BinaryCompressor.BoundedResult.Corrupt -> {
                    rejected = true
                    AppLogger.d(
                        "PayloadDispatcher",
                        "MEDIA_REJECT reason=corrupt bytes=${r.bytes} cause=${r.cause}"
                    )
                    rejectTrace(TraceDropReason.MEDIA_REJECT_CORRUPT, endpointId, msgId, isAudio)
                    null
                }
            }
        }
        val image = inflate(payload.imageBytes, isAudio = false)
        val audio = inflate(payload.audioBytes, isAudio = true)
        return InboundMedia(image, audio, rejected)
    }

    /** Forwards a private message to the next hop on its directed route; never floods. */
    private fun relayPrivate(
        payload: MeshPayload,
        routePath: MutableList<String>,
        callback: PayloadDispatcherCallback
    ) {
        AppLogger.d(
            "PayloadDispatcher",
            "ROUTE (Relay): Forwarding Private message meant for " +
                "[${AppLogger.redactName(payload.targetName)}] securely across the mesh."
        )
        routePath.add(callback.getMyDeviceName())
        val updatedBytes = ProtoBuf.encodeToByteArray(payload.copy(routePath = routePath))
        val nextNode = nextHopOnDirectedRoute(payload.directedRouteNodeIds, callback.getMyNodeId())
        if (nextNode != null) {
            val result = callback.forwardPrivatePayload(nextNode, updatedBytes)
            AppLogger.d("PayloadDispatcher", "Private relay next-hop dispatch: $result")
            return
        }
        AppLogger.d("PayloadDispatcher", "Private relay route unavailable; dropping without broadcast")
    }

    /** Fields absent from the decrypted envelope are null and keep the caller's current values. */
    private class PrivateContent(
        val text: String,
        val imageBase64: String?,
        val audioBase64: String?,
        val locationLat: Double?,
        val locationLng: Double?
    )

    /** Null after logging the reason when the envelope is missing, cannot be decrypted or is malformed. */
    private fun decryptPrivateContent(payload: MeshPayload): PrivateContent? {
        AppLogger.d("MeshNetwork_E2EE", "Message is addressed to ME. Attempting RSA+AES Hybrid Decryption...")
        return decryptEnvelope(payload)?.let(::parsePrivateContent)
    }

    private fun decryptEnvelope(payload: MeshPayload): String? {
        val encryptedData = payload.encryptedData
        val encryptedKey = payload.encryptedKey
        if (encryptedData.isNullOrBlank() || encryptedKey.isNullOrBlank()) {
            AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Missing encrypted envelope")
            return null
        }
        return CryptoManager.decryptHybrid(encryptedData, encryptedKey).also { decrypted ->
            if (decrypted == null) AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Could not decrypt payload")
        }
    }

    private fun parsePrivateContent(decryptedJsonString: String): PrivateContent? = try {
        val innerPayload = org.json.JSONObject(decryptedJsonString)
        val content = PrivateContent(
            text = innerPayload.getString("text"),
            imageBase64 = if (innerPayload.has("image")) innerPayload.getString("image") else null,
            audioBase64 = if (innerPayload.has("audio")) innerPayload.getString("audio") else null,
            locationLat = if (innerPayload.has("locationLat")) innerPayload.getDouble("locationLat") else null,
            locationLng = if (innerPayload.has("locationLng")) innerPayload.getDouble("locationLng") else null
        )
        AppLogger.d("MeshNetwork_E2EE", "E2EE SUCCESS: Decrypted private payload")
        content
    } catch (e: Exception) {
        AppLogger.d("MeshNetwork_E2EE", "E2EE FAILED: Invalid encrypted content")
        null
    }
}

private const val ATTACHMENT_UNAVAILABLE = "[Attachment unavailable]"

/** Clamps the sender's text first so the local inbound clamp can never cut the marker off. */
private fun withAttachmentUnavailableMarker(text: String): String {
    val room = edu.cit.resqmesh.core.security.input.TextField.MESSAGE.maxLength -
        ATTACHMENT_UNAVAILABLE.length - 1
    val kept = edu.cit.resqmesh.core.security.input.TextPolicy.clampLength(text, room)
    return if (kept.isBlank()) ATTACHMENT_UNAVAILABLE else "$kept\n$ATTACHMENT_UNAVAILABLE"
}

private fun logInboundProtection(isEncrypted: Boolean, sender: String) {
    if (isEncrypted) {
        AppLogger.d("MeshNetwork_E2EE", "INBOUND ENCRYPTED PAYLOAD DETECTED from ${AppLogger.redactName(sender)}")
    } else {
        AppLogger.d(
            "MeshNetwork_E2EE",
            "INBOUND PUBLIC PAYLOAD DETECTED from ${AppLogger.redactName(sender)}. No encryption applied."
        )
    }
}

/** A stable node-id target wins; legacy payloads without one fall back to the device name. */
private fun isAddressedToMe(payload: MeshPayload, callback: PayloadDispatcherCallback): Boolean {
    val targetNodeId = payload.targetNodeId
    return targetNodeId.isNotBlank() && targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true) ||
        (targetNodeId.isBlank() && NodeIdentity.matches(payload.targetName, callback.getMyDeviceName()))
}

/** The node after [myNodeId] on [routeIds], or null when this node is absent or last. */
private fun nextHopOnDirectedRoute(routeIds: List<String>, myNodeId: String): String? {
    val myIndex = routeIds.indexOf(myNodeId)
    return if (myIndex >= 0) routeIds.getOrNull(myIndex + 1) else null
}
