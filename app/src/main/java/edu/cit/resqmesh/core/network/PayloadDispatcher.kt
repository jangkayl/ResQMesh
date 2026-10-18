package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.network.dispatch.BlockAckHandler
import edu.cit.resqmesh.core.network.dispatch.BlockRequestHandler
import edu.cit.resqmesh.core.network.dispatch.LegacyBlockControlHandler
import edu.cit.resqmesh.core.network.dispatch.GoodbyeHandler
import edu.cit.resqmesh.core.network.dispatch.InboundPayloadValidator
import edu.cit.resqmesh.core.network.ratelimit.InboundGuards
import edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate
import edu.cit.resqmesh.core.network.dispatch.LiveAudioHandler
import edu.cit.resqmesh.core.network.dispatch.PayloadHandler
import edu.cit.resqmesh.core.network.dispatch.ReceiptHandler
import edu.cit.resqmesh.core.network.dispatch.StandardMessageHandler
import edu.cit.resqmesh.core.network.dispatch.SystemPulseHandler
import edu.cit.resqmesh.core.network.dispatch.PingHandler
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

import edu.cit.resqmesh.core.network.dispatch.DomainEventHandler
import edu.cit.resqmesh.core.network.dispatch.EventSyncRequestHandler
import edu.cit.resqmesh.core.network.dispatch.EventSyncResponseHandler

@OptIn(ExperimentalSerializationApi::class)
class PayloadDispatcher(
    private val callback: PayloadDispatcherCallback,
    private val validator: InboundPayloadValidator = InboundPayloadValidator(),
    private val originGate: InboundOriginGate = InboundGuards().origins
) {

    private val handlers: List<PayloadHandler> = listOf(
        SystemPulseHandler(),
        PingHandler(),
        ReceiptHandler(),
        GoodbyeHandler(),
        BlockRequestHandler(),
        BlockAckHandler(),
        LegacyBlockControlHandler(),
        DomainEventHandler(),
        EventSyncRequestHandler(),
        EventSyncResponseHandler(),
        LiveAudioHandler(),
        StandardMessageHandler()
    )

    private fun validated(endpointId: String, decoded: MeshPayload): MeshPayload? =
        when (val verdict = validator.validate(decoded)) {
            is InboundPayloadValidator.Result.Accepted -> verdict.payload
            is InboundPayloadValidator.Result.Rejected -> {
                AppLogger.event(
                    category = TerminalLogCategory.SECURITY,
                    event = "INBOUND_REJECT",
                    message = "INBOUND_REJECT type=${decoded.type.take(MAX_TYPE_LOG)} reason=${verdict.reason}",
                    level = TerminalLogLevel.WARN,
                    tag = "PAYLOAD_DISPATCHER",
                    endpoint = endpointId
                )
                null
            }
        }

    fun dispatch(endpointId: String, payloadBytes: ByteArray) {
        try {
            val decoded = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            val payload = validated(endpointId, decoded) ?: return
            // Relay handlers forward the raw bytes, so a clamped TTL must be re-encoded.
            val relayBytes = if (payload === decoded) payloadBytes else ProtoBuf.encodeToByteArray(payload)

            // 1. DUPLICATE MESSAGE CHECK (THE BOUNCER)
            if (payload.id.isEmpty()) return
            // SOS validates signatures before dedup/relay. Conversation storage owns durable
            // dedup so duplicate retries can still receive receipts after a lost acknowledgement.
            if (payload.type in setOf("SOS_EVENT", "SOS_SYNC_REQ", "SOS_SYNC_RES")) {
                if (!originGate.admit(endpointId, payload, callback.getMyNodeId())) return
                callback.onSosPacket(endpointId, payload)
                return
            }
            if (payload.type == "CONVERSATION") {
                if (!originGate.admit(endpointId, payload, callback.getMyNodeId())) return
                callback.onConversationMessage(endpointId, payload)
                return
            }
            val msgId = payload.id

            val localPrivate = payload.type == "MESSAGE" && payload.isPrivate && payload.isEncrypted &&
                (payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true) && payload.targetNodeId.isNotBlank() ||
                    payload.targetNodeId.isBlank() && edu.cit.resqmesh.core.model.NodeIdentity.matches(payload.targetName, callback.getMyDeviceName()))
            // The recipient store deduplicates private rows, then replays lost receipts.
            val seenBefore = callback.getSeenIdCache().contains(msgId)
            if (seenBefore && !localPrivate && !callback.hasPendingCustody(msgId)) return

            val sender = payload.senderName
            if (sender == callback.getMyDeviceName()) return
            if (!originGate.admit(endpointId, payload, callback.getMyNodeId())) return
            // Only admitted ids are remembered, so a rate-limited payload can be retried with the same id.
            callback.getSeenIdCache().markSeen(msgId)

            // 2. STRATEGY ROUTING LOGIC
            val handler = handlers.firstOrNull { it.canHandle(payload.type) }
            handler?.handle(endpointId, payload, relayBytes, callback)
                ?: AppLogger.event(
                    category = TerminalLogCategory.SYSTEM,
                    event = "PAYLOAD_REJECTED",
                    message = "No handler found for payload type ${payload.type}",
                    level = TerminalLogLevel.WARN,
                    tag = "PAYLOAD_DISPATCHER",
                    endpoint = endpointId
                )
                
        } catch (e: Exception) {
            AppLogger.event(
                category = TerminalLogCategory.SYSTEM,
                event = "PAYLOAD_PARSE_FAILED",
                message = "Could not parse incoming payload: ${e.message}",
                level = TerminalLogLevel.ERROR,
                tag = "PAYLOAD_DISPATCHER",
                endpoint = endpointId
            )
        }
    }

    private companion object {
        const val MAX_TYPE_LOG = 32
    }
}
