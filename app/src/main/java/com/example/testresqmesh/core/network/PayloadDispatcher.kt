package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.network.dispatch.BlockAckHandler
import com.example.testresqmesh.core.network.dispatch.BlockRequestHandler
import com.example.testresqmesh.core.network.dispatch.LegacyBlockControlHandler
import com.example.testresqmesh.core.network.dispatch.GoodbyeHandler
import com.example.testresqmesh.core.network.dispatch.LiveAudioHandler
import com.example.testresqmesh.core.network.dispatch.PayloadHandler
import com.example.testresqmesh.core.network.dispatch.ReceiptHandler
import com.example.testresqmesh.core.network.dispatch.StandardMessageHandler
import com.example.testresqmesh.core.network.dispatch.SystemPulseHandler
import com.example.testresqmesh.core.network.dispatch.PingHandler
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import com.example.testresqmesh.core.utils.TerminalLogLevel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

import com.example.testresqmesh.core.network.dispatch.DomainEventHandler
import com.example.testresqmesh.core.network.dispatch.EventSyncRequestHandler
import com.example.testresqmesh.core.network.dispatch.EventSyncResponseHandler

@OptIn(ExperimentalSerializationApi::class)
class PayloadDispatcher(private val callback: PayloadDispatcherCallback) {

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

    fun dispatch(endpointId: String, payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            
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
                    payload.targetNodeId.isBlank() && com.example.testresqmesh.core.model.NodeIdentity.matches(payload.targetName, callback.getMyDeviceName()))
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
