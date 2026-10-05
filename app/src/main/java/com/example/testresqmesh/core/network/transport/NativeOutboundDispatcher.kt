package com.example.testresqmesh.core.network.transport

import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.BroadcastDispatchResult
import com.example.testresqmesh.core.network.DirectEndpoint
import com.example.testresqmesh.core.network.DirectSendPolicy
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.ReliableMeshTransfers
import com.example.testresqmesh.core.network.TransferPiece
import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.GattTransfer
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.network.bluetooth.state.RetainedFallbackQueue
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/** Outbound coordination only; the existing engines, handler and state store are shared. */
@OptIn(ExperimentalSerializationApi::class)
internal class NativeOutboundDispatcher(
    private val store: BleStateStore,
    private val handler: Handler,
    private val peerTransferVersions: Map<String, Int>,
    private val nodeIdForEndpoint: (String) -> String?,
    private val hasReadyEndpoint: (String) -> Boolean,
    private val isDeviceBlocked: (String) -> Boolean,
    private val hasUsableL2cap: (String) -> Boolean,
    private val transfers: () -> ReliableMeshTransfers,
    private val coordinator: () -> GattTransferCoordinator,
    private val sendL2cap: (String, ByteArray, Boolean) -> TransportDispatchResult,
    private val processNextPayload: (String) -> Unit,
    private val reportFrame: (String, ByteArray, OutboundFrameEvent.Stage, String) -> Unit,
    private val removeHeartbeat: (String) -> Unit
) {
    private val reliableTransfers get() = transfers()
    private val transferCoordinator get() = coordinator()
    private val legacyRelayFrames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private data class FallbackFrame(val bytes: ByteArray, val priority: Boolean)
    private val retainedFallback = RetainedFallbackQueue<FallbackFrame>(
        send = { endpoint, frame -> enqueueGattPayload(endpoint, frame.bytes, frame.priority) },
        schedule = { delay, action -> handler.postDelayed({ action() }, delay) },
        failed = { endpoint, frame -> reportFrame(endpoint, frame.bytes, OutboundFrameEvent.Stage.FAILED, "GATT") }
    )

    fun retainFailedL2cap(endpoint: String, payload: ByteArray) {
        retainedFallback.retain(endpoint, listOf(FallbackFrame(payload, isControlPayload(payload))))
    }

    fun hasPendingFallback(endpoint: String) = retainedFallback.hasPending(endpoint)
    fun flushFallback() = retainedFallback.flush()
    fun retireFallback(endpoint: String) = retainedFallback.retire(endpoint)
    fun clearFallback() = retainedFallback.clear()
    fun clearLegacyFrames() = legacyRelayFrames.clear()
    fun removeLegacyFrame(key: String, endpoint: String) = legacyRelayFrames.remove(key, endpoint)
    fun retireLegacyFrames(endpoint: String) {
        legacyRelayFrames.entries.removeAll { it.value == endpoint }
    }

    fun forwardPrivatePayload(nodeId: String, payload: ByteArray): TransportDispatchResult {
        return if (peerTransferVersions[nodeId.uppercase()] == ReliableMeshTransfers.VERSION) {
            reliableTransfers.offer(nodeId.uppercase(), payload, false, durableRelay = true)
        } else {
            val endpoint = endpointForPeer(nodeId) ?: return TransportDispatchResult.REJECTED_NOT_READY
            sendLegacyRelay(nodeId.uppercase(), endpoint, payload)
        }
    }

    fun endpointForPeer(nodeId: String): String? = DirectSendPolicy.select(
        (store.activeConnections.keys + store.activeServerConnections.keys).distinct().mapNotNull { endpoint ->
            val id = nodeIdForEndpoint(endpoint) ?: return@mapNotNull null
            val name = store.connectedEndpointNames[endpoint] ?: return@mapNotNull null
            if (!id.equals(nodeId, true) || !hasReadyEndpoint(endpoint) || NodeIdentity.isPlaceholder(name) || isDeviceBlocked(name)) null
            else DirectEndpoint(endpoint, id, hasUsableL2cap(endpoint),
                !com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy.isUnresponsive(
                    store.connectionInteractionTimes[endpoint] ?: 0, System.currentTimeMillis()))
        }
    )?.endpoint

    fun isControlPayload(bytes: ByteArray): Boolean = runCatching {
        val p = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
        p.type in setOf("SYSTEM", "PING", "PONG", "SEEN", "DELIVERED", "BLOCK_REQUEST", "BLOCK_ACK",
            "SOS_EVENT", "SOS_SYNC_REQ", "SOS_SYNC_RES", "EVENT_SYNC_REQ", ReliableMeshTransfers.ACK) ||
            (p.type == ReliableMeshTransfers.PIECE && ProtoBuf.decodeFromByteArray<TransferPiece>(p.liveAudioChunk!!).let { it.priority || it.status })
    }.getOrDefault(false)

    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        cacheOutgoingMessageId(payloadBytes)
        val p = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes) }.getOrNull()
        val excludedPeer = excludeEndpointId?.let(nodeIdForEndpoint)
        val visited = if (p != null && !p.isPrivate && p.type in setOf("MESSAGE", "CONVERSATION"))
            (p.routePath + p.senderName).mapNotNull(NodeIdentity::idOf).map { it.uppercase() }.toSet() else emptySet()
        val peers = (store.activeConnections.keys + store.activeServerConnections.keys).distinct()
            .filter { it != excludeEndpointId && hasReadyEndpoint(it) }.mapNotNull(nodeIdForEndpoint)
            .map { it.uppercase() }.filter { it !in visited && !it.equals(excludedPeer, true) && endpointForPeer(it) != null }.toSet()
        val custody = p != null && excludeEndpointId != null && reliableTransfers.hasPendingCustody(p.id)
        val roster = if (custody) reliableTransfers.relayTargets(p!!.id, peers) else peers
        return BroadcastDispatchResult(roster.associateWith { peer ->
            if (custody && reliableTransfers.alreadyForwarded(p!!.id, peer)) TransportDispatchResult.ACCEPTED
            else if (custody && peerTransferVersions[peer] == ReliableMeshTransfers.VERSION) {
                reliableTransfers.offer(peer, payloadBytes, false, durableRelay = true)
            } else endpointForPeer(peer)?.let {
                if (custody) sendLegacyRelay(peer, it, payloadBytes) else sendDirectPayload(it, payloadBytes)
            } ?: TransportDispatchResult.REJECTED_NOT_READY
        })
    }

    private fun sendLegacyRelay(peer: String, endpoint: String, bytes: ByteArray): TransportDispatchResult {
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull()
            ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        if (!reliableTransfers.hasPendingCustody(payload.id)) return sendDirectPayload(endpoint, bytes)
        val key = "$peer:${payload.id}"
        if (legacyRelayFrames.putIfAbsent(key, endpoint) != null) return TransportDispatchResult.ACCEPTED
        return sendDirectPayload(endpoint, bytes).also { if (!it.accepted) legacyRelayFrames.remove(key, endpoint) }
    }

    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        cacheOutgoingMessageId(payloadBytes)
        val targets = mutableSetOf<String>()
        targets.addAll(store.activeConnections.keys)
        targets.addAll(store.activeServerConnections.keys)
        targets.remove(excludeEndpointId)
        return BroadcastDispatchResult(targets.associateWith { sendPriorityPayload(it, payloadBytes) })
    }

    fun enqueueGattPayload(
        endpoint: String,
        payloadBytes: ByteArray,
        priority: Boolean = false,
        heartbeatId: String? = null
    ): Boolean {
        if (!hasReadyEndpoint(endpoint)) return false
        val frame = MeshFrameCodec.encode(payloadBytes) ?: return false
        if (!transferCoordinator.enqueue(endpoint, GattTransfer(frame, heartbeatId), priority)) {
            AppLogger.d("BLE_MESH", "GATT queue full for $endpoint; rejected payload")
            return false
        }
        processNextPayload(endpoint)
        return true
    }

    fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult {
        return dispatchPayload(targetEndpointId, payloadBytes, true)
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray): TransportDispatchResult {
        return dispatchPayload(targetMacAddress, payloadBytes, isControlPayload(payloadBytes))
    }

    private fun dispatchPayload(endpoint: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        if (!BluetoothAdapter.checkBluetoothAddress(endpoint)) return TransportDispatchResult.REJECTED_INVALID_ENDPOINT
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull()
            ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        val peer = nodeIdForEndpoint(endpoint)?.uppercase()
        val selected = if (peer != null && DirectSendPolicy.usesPeerEndpoint(payload.type)) endpointForPeer(peer) ?: endpoint else endpoint
        if (!hasReadyEndpoint(selected)) return TransportDispatchResult.REJECTED_NOT_READY
        if (payload.type in setOf("MESSAGE", "CONVERSATION") && !store.links.isIdentityAdmitted(selected)) {
            return TransportDispatchResult.REJECTED_NOT_READY
        }
        cacheOutgoingMessageId(bytes)
        return if (peer != null && peerTransferVersions[peer] == ReliableMeshTransfers.VERSION && DirectSendPolicy.usesPieces(payload.type, bytes.size)) {
            reliableTransfers.offer(peer, bytes, priority, durableRelay = true)
        } else sendRawPayload(selected, bytes, priority)
    }

    fun sendRawPayload(endpoint: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        if (!com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy.fitsSingleTransfer(bytes.size + Int.SIZE_BYTES, priority)) {
            return TransportDispatchResult.REJECTED_INVALID_FRAME
        }
        if (!hasReadyEndpoint(endpoint)) return TransportDispatchResult.REJECTED_NOT_READY
        // The failed socket's accepted frames retain ownership before new ordinary work is admitted.
        if (!priority && retainedFallback.hasPending(endpoint)) return TransportDispatchResult.REJECTED_QUEUE_FULL
        val l2capResult = sendL2cap(endpoint, bytes, priority)
        if (l2capResult != TransportDispatchResult.REJECTED_NOT_READY) {
            if (l2capResult.accepted) reportFrame(endpoint, bytes, OutboundFrameEvent.Stage.QUEUED, "L2CAP")
            return l2capResult
        }
        val frame = MeshFrameCodec.encode(bytes) ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        if (!transferCoordinator.enqueue(endpoint, GattTransfer(frame), priority)) return TransportDispatchResult.REJECTED_QUEUE_FULL
        reportFrame(endpoint, bytes, OutboundFrameEvent.Stage.QUEUED, "GATT")
        processNextPayload(endpoint)
        return TransportDispatchResult.ACCEPTED
    }

    fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            if (payload.id.isNotEmpty()) store.seenMessageIds.add(payload.id)
        } catch (e: Exception) {}
    }

    fun promoteGattWorkToL2cap(endpoint: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { promoteGattWorkToL2cap(endpoint) }
            return
        }
        if (!hasUsableL2cap(endpoint)) return

        val activeHeartbeatId = store.gattFlights[endpoint]?.transfer?.heartbeatId
        val promoted = transferCoordinator.drainForPromotion(endpoint)
        if (activeHeartbeatId != null) removeHeartbeat(endpoint)
        if (promoted.isEmpty()) return

        AppLogger.d("BLE_MESH", "Promoting ${promoted.size} queued GATT transfer(s) to L2CAP for $endpoint")
        var deferred = false
        promoted.forEach { transfer ->
            val result = if (deferred) TransportDispatchResult.REJECTED_QUEUE_FULL
                else sendL2cap(endpoint, transfer.payloadBytes(), transfer.priority)
            if (!result.accepted) {
                deferred = true
                // Keep deferred promotion work until the socket writer frees capacity. Never lose
                // an already accepted frame or create a second GATT lane beside a congested socket.
                store.pendingQueues[endpoint]?.addLast(transfer)
                AppLogger.d("BLE_MESH", "L2CAP promotion deferred on $endpoint: $result")
            }
        }
    }
}
