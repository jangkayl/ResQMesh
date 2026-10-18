package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.network.dispatch.BlockAckHandler
import edu.cit.resqmesh.core.network.dispatch.BlockRequestHandler
import edu.cit.resqmesh.core.network.dispatch.LegacyBlockControlHandler
import edu.cit.resqmesh.core.network.dispatch.GoodbyeHandler
import edu.cit.resqmesh.core.network.dispatch.InboundPayloadValidator
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
    private val validator: InboundPayloadValidator = InboundPayloadValidator()
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

    fun dispatch(endpointId: String, rawBytes: ByteArray) {
        try {
            val decoded = ProtoBuf.decodeFromByteArray<MeshPayload>(rawBytes)
            val payload = when (val verdict = validator.validate(decoded)) {
                is InboundPayloadValidator.Result.Rejected -> {
                    AppLogger.event(
                        category = TerminalLogCategory.SECURITY,
                        event = "INBOUND_REJECT",
                        message = "INBOUND_REJECT type=${decoded.type.take(32)} reason=${verdict.reason}",
                        level = TerminalLogLevel.WARN,
                        tag = "PAYLOAD_DISPATCHER",
                        endpoint = endpointId
                    )
                    return
                }
                is InboundPayloadValidator.Result.Accepted -> verdict.payload
            }
            // Relay handlers forward the raw bytes, so a clamped TTL must be re-encoded.
            val payloadBytes = if (payload === decoded) rawBytes else ProtoBuf.encodeToByteArray(payload)

            // 1. DUPLICATE MESSAGE CHECK (THE BOUNCER)
            if (payload.id.isEmpty()) return
            // SOS validates signatures before dedup/relay. Conversation storage owns durable
            // dedup so duplicate retries can still receive receipts after a lost acknowledgement.
            if (payload.type in setOf("SOS_EVENT", "SOS_SYNC_REQ", "SOS_SYNC_RES")) {
                callback.onSosPacket(endpointId, payload)
                return
            }
            if (payload.type == "CONVERSATION") {
                callback.onConversationMessage(endpointId, payload)
                return
            }
            val msgId = payload.id
            val seenMessageIds = callback.getSeenMessageIds()

            val localPrivate = payload.type == "MESSAGE" && payload.isPrivate && payload.isEncrypted &&
                (payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true) && payload.targetNodeId.isNotBlank() ||
                    payload.targetNodeId.isBlank() && edu.cit.resqmesh.core.model.NodeIdentity.matches(payload.targetName, callback.getMyDeviceName()))
            // The recipient store deduplicates private rows, then replays lost receipts.
            if (seenMessageIds.contains(msgId) && !localPrivate && !callback.hasPendingCustody(msgId)) return
            seenMessageIds.add(msgId)
            if (seenMessageIds.size > 500) {
                seenMessageIds.firstOrNull()?.let { oldest ->
                    seenMessageIds.remove(oldest)
                }
            }

            val sender = payload.senderName
            if (sender == callback.getMyDeviceName()) return

            // 2. STRATEGY ROUTING LOGIC
            val handler = handlers.firstOrNull { it.canHandle(payload.type) }
            handler?.handle(endpointId, payload, payloadBytes, callback)
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
}
