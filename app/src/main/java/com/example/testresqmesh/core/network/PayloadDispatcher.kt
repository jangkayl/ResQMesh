package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.network.dispatch.BlockHandler
import com.example.testresqmesh.core.network.dispatch.GoodbyeHandler
import com.example.testresqmesh.core.network.dispatch.LiveAudioHandler
import com.example.testresqmesh.core.network.dispatch.PayloadHandler
import com.example.testresqmesh.core.network.dispatch.ReceiptHandler
import com.example.testresqmesh.core.network.dispatch.StandardMessageHandler
import com.example.testresqmesh.core.network.dispatch.SystemPulseHandler
import com.example.testresqmesh.core.network.dispatch.UnblockHandler
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class PayloadDispatcher(private val callback: PayloadDispatcherCallback) {

    private val handlers: List<PayloadHandler> = listOf(
        SystemPulseHandler(),
        ReceiptHandler(),
        GoodbyeHandler(),
        BlockHandler(),
        UnblockHandler(),
        LiveAudioHandler(),
        StandardMessageHandler()
    )

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

            // 2. STRATEGY ROUTING LOGIC
            val handler = handlers.firstOrNull { it.canHandle(payload.type) }
            handler?.handle(endpointId, payload, payloadBytes, callback)
                ?: AppLogger.d("PAYLOAD_DISPATCHER", "No handler found for payload type: ${payload.type}")
                
        } catch (e: Exception) {
            AppLogger.d("PAYLOAD_DISPATCHER", "Error parsing Protobuf payload: ${e.message}")
        }
    }
}
