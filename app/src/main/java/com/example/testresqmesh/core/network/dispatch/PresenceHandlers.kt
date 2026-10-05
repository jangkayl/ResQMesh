package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogCategory
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class SystemPulseHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "SYSTEM"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val sender = payload.senderName
        val msgId = payload.id
        // Direct-link identity binding happens before dispatch in NativeBleManager. A SYSTEM pulse
        // can be forwarded by a different physical peer, so it must never rename this endpoint.
        val senderNodeId = payload.senderNodeId.ifBlank { com.example.testresqmesh.core.model.NodeIdentity.idOf(sender).orEmpty() }
        if (payload.publicKey.isNotEmpty()) {
            callback.onPublicKeyReceived(sender, senderNodeId, payload.publicKey)
        }
        // An empty adjacency is an authoritative withdrawal, not "no update".
        callback.onRoutingTableReceived(
            sender,
            senderNodeId,
            payload.connectedNodes,
            payload.connectedNodeIds,
            payload.topologySequence
        )
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "SYSTEM_RECEIVED",
            message = "SYSTEM pulse received; ${payload.connectedNodes.size} advertised neighbor(s)",
            peerName = sender,
            endpoint = endpointId
        )

        callback.onMessageReceived(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL", emptyList(), payload.channelId)

        val canRelay = if (payload.ttl > 0) {
            payload.ttl > 1
        } else {
            payload.relayHopCount < MAX_SYSTEM_RELAY_HOPS
        }
        if (!canRelay) return
        val routePath = payload.routePath.toMutableList()
        routePath.add(callback.getMyDeviceName())
        val updatedPayload = payload.copy(
            routePath = routePath,
            relayHopCount = payload.relayHopCount + 1,
            ttl = if (payload.ttl > 0) payload.ttl - 1 else 0
        )
        val updatedBytes = ProtoBuf.encodeToByteArray(updatedPayload)
        callback.broadcastPayload(updatedBytes, endpointId)
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "SYSTEM_RELAYED",
            message = "SYSTEM pulse forwarded to eligible direct peers",
            peerName = sender,
            endpoint = endpointId,
            isVerbose = true
        )
    }

    private companion object {
        const val MAX_SYSTEM_RELAY_HOPS = 4
    }
}

@OptIn(ExperimentalSerializationApi::class)
class PingHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "PING" || payloadType == "PONG"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (payload.type == "PONG") {
            callback.onHeartbeatAck(endpointId, payload.targetMessageId)
            AppLogger.event(
                category = TerminalLogCategory.SYNC,
                event = "HEARTBEAT_ACK",
                message = "Heartbeat acknowledgement received",
                peerName = payload.senderName,
                endpoint = endpointId,
                isVerbose = true
            )
            return
        }
        callback.onDeviceNameSync(endpointId, payload.senderName)
        AppLogger.event(
            category = TerminalLogCategory.SYNC,
            event = "HEARTBEAT_RECEIVED",
            message = "PING received; replying when it is a heartbeat challenge",
            peerName = payload.senderName,
            endpoint = endpointId,
            isVerbose = true
        )
        if (payload.id.startsWith("HB:")) {
            val reply = MeshPayload(
                id = "HA:${java.util.UUID.randomUUID()}",
                type = "PONG",
                senderName = callback.getMyDeviceName(),
                targetMessageId = payload.id
            )
            callback.sendGattPayload(endpointId, ProtoBuf.encodeToByteArray(reply))
        }
    }
}
