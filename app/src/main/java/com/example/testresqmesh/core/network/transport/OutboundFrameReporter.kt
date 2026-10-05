package com.example.testresqmesh.core.network.transport

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/** Reports local progress through current listeners; it never establishes recipient delivery. */
@OptIn(ExperimentalSerializationApi::class)
internal class OutboundFrameReporter(
    private val readyGattLink: (String) -> BleLink?,
    private val nodeIdForEndpoint: (String) -> String?,
    private val transfers: () -> ReliableMeshTransfers,
    private val removeLegacyFrame: (String, String) -> Boolean,
    private val listener: () -> ((OutboundFrameEvent) -> Unit)?
) {
    private val reliableTransfers get() = transfers()
    private val onOutboundFrame get() = listener()

    fun reportFrame(endpoint: String, bytes: ByteArray, stage: OutboundFrameEvent.Stage,
                            transport: String, queuedAt: Long = 0L, startedAt: Long = 0L) {
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull() ?: return
        if (payload.type == ReliableMeshTransfers.PIECE) { reliableTransfers.frame(payload, stage); return }
        if (payload.type !in setOf("MESSAGE", "CONVERSATION")) return
        val now = System.currentTimeMillis()
        val event = OutboundFrameEvent(payload.id, payload.targetMessageId.ifBlank { payload.id }, stage, transport,
            readyGattLink(endpoint)?.generation ?: 0L, android.os.SystemClock.elapsedRealtime(), bytes.size,
            if (queuedAt > 0 && startedAt > 0) (startedAt - queuedAt).coerceAtLeast(0) else 0,
            if (startedAt > 0) (now - startedAt).coerceAtLeast(0) else 0,
            payload.isPrivate, (payload.directedRouteNodeIds.size - 1).coerceAtLeast(1))
        AppLogger.d("BLE_TRANSFER", "FRAME_${stage.name} transmission=${event.transmissionId} message=${event.messageId} transport=$transport bytes=${event.bytes} queueMs=${event.queueMs} transferMs=${event.transferMs}")
        if (stage == OutboundFrameEvent.Stage.COMPLETED && transport != "TRANSFER") {
            nodeIdForEndpoint(endpoint)?.let { peer ->
                if (removeLegacyFrame("${peer.uppercase()}:${payload.id}", endpoint)) {
                    reliableTransfers.legacyForwarded(peer.uppercase(), payload)
                }
            }
        } else if (stage == OutboundFrameEvent.Stage.FAILED && transport != "TRANSFER") {
            nodeIdForEndpoint(endpoint)?.let { removeLegacyFrame("${it.uppercase()}:${payload.id}", endpoint) }
        }
        onOutboundFrame?.invoke(event)
    }
}
