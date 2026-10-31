package edu.cit.resqmesh.core.network

import edu.cit.resqmesh.core.network.dispatch.BlockAckHandler
import edu.cit.resqmesh.core.network.dispatch.BlockRequestHandler
import edu.cit.resqmesh.core.network.dispatch.LegacyBlockControlHandler
import edu.cit.resqmesh.core.network.dispatch.GoodbyeHandler
import edu.cit.resqmesh.core.network.dispatch.InboundPayloadValidator
import edu.cit.resqmesh.core.network.ratelimit.InboundGuards
import edu.cit.resqmesh.core.network.ratelimit.InboundOriginGate
import edu.cit.resqmesh.core.network.dispatch.PayloadHandler
import edu.cit.resqmesh.core.network.dispatch.ReceiptHandler
import edu.cit.resqmesh.core.network.dispatch.StandardMessageHandler
import edu.cit.resqmesh.core.network.dispatch.SystemPulseHandler
import edu.cit.resqmesh.core.network.dispatch.PingHandler
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.LogFields
import edu.cit.resqmesh.core.logging.TerminalLogCategory
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.core.logging.trace.TraceDropReason
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
        StandardMessageHandler()
    )

    private fun validated(endpointId: String, decoded: MeshPayload): MeshPayload? =
        when (val verdict = validator.validate(decoded)) {
            is InboundPayloadValidator.Result.Accepted -> verdict.payload
            is InboundPayloadValidator.Result.Rejected -> {
                AppLogger.event(
                    category = TerminalLogCategory.SECURITY,
                    event = "INBOUND_REJECT",
                    message = "INBOUND_REJECT type=${LogFields.type(decoded.type)} reason=${verdict.reason}",
                    level = TerminalLogLevel.WARN,
                    tag = "PAYLOAD_DISPATCHER",
                    endpoint = endpointId
                )
                null
            }
        }

    private fun admit(endpointId: String, payload: MeshPayload) =
        originGate.admit(endpointId, payload, callback.getMyNodeId(), callback.getAdmittedNodeId(endpointId))

    /** Observe-only drop event; transport is unknown here and the emitter is a no-op when tracing is off. */
    private fun drop(payload: MeshPayload, reason: TraceDropReason, endpointId: String, payloadBytes: ByteArray) =
        PacketTraceEmitter.drop(payload, reason, endpointId, payloadBytes.size, null)

    fun dispatch(endpointId: String, payloadBytes: ByteArray) {
        try {
            val decoded = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            val payload = validated(endpointId, decoded) ?: run {
                drop(decoded, TraceDropReason.VALIDATOR_REJECTED, endpointId, payloadBytes)
                return
            }
            // Relay handlers forward the raw bytes, so a clamped TTL must be re-encoded.
            val relayBytes = if (payload === decoded) payloadBytes else ProtoBuf.encodeToByteArray(payload)

            // 1. DUPLICATE MESSAGE CHECK (THE BOUNCER)
            if (payload.id.isEmpty()) {
                drop(payload, TraceDropReason.EMPTY_ID, endpointId, payloadBytes)
                return
            }
            // SOS validates signatures before dedup/relay. Conversation storage owns durable
            // dedup so duplicate retries can still receive receipts after a lost acknowledgement.
            if (payload.type in setOf("SOS_EVENT", "SOS_SYNC_REQ", "SOS_SYNC_RES")) {
                // SOS_EVENT is rate limited by SosRepository once its signature verified.
                if (payload.type != "SOS_EVENT" && !admit(endpointId, payload)) {
                    drop(payload, TraceDropReason.ORIGIN_LIMIT, endpointId, payloadBytes)
                    return
                }
                callback.onSosPacket(endpointId, payload)
                return
            }
            if (payload.type == "CONVERSATION") {
                if (!admit(endpointId, payload)) {
                    drop(payload, TraceDropReason.ORIGIN_LIMIT, endpointId, payloadBytes)
                    return
                }
                callback.onConversationMessage(endpointId, payload)
                return
            }
            val msgId = payload.id

            val localPrivate = payload.type == "MESSAGE" && payload.isPrivate && payload.isEncrypted &&
                (payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true) && payload.targetNodeId.isNotBlank() ||
                    payload.targetNodeId.isBlank() && edu.cit.resqmesh.core.model.NodeIdentity.matches(payload.targetName, callback.getMyDeviceName()))
            // The recipient store deduplicates private rows, then replays lost receipts.
            val seenBefore = callback.getSeenIdCache().contains(msgId)
            if (seenBefore && !localPrivate && !callback.hasPendingCustody(msgId)) {
                drop(payload, TraceDropReason.DUPLICATE, endpointId, payloadBytes)
                return
            }

            val sender = payload.senderName
            if (sender == callback.getMyDeviceName()) {
                drop(payload, TraceDropReason.SELF_ORIGIN, endpointId, payloadBytes)
                return
            }
            if (!admit(endpointId, payload)) {
                drop(payload, TraceDropReason.ORIGIN_LIMIT, endpointId, payloadBytes)
                return
            }
            // Only admitted ids are remembered, so a rate-limited payload can be retried with the same id.
            callback.getSeenIdCache().markSeen(msgId)

            // 2. STRATEGY ROUTING LOGIC
            val handler = handlers.firstOrNull { it.canHandle(payload.type) }
            if (handler == null) drop(payload, TraceDropReason.NO_HANDLER, endpointId, payloadBytes)
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
                message = "Could not parse incoming payload: ${e.javaClass.simpleName}",
                level = TerminalLogLevel.ERROR,
                tag = "PAYLOAD_DISPATCHER",
                endpoint = endpointId
            )
        }
    }
}
