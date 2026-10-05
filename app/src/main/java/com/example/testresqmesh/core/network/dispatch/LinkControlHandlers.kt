package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.BlockControlKind
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

class GoodbyeHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "GOODBYE"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onDeviceGoodbye(endpointId)
        callback.broadcastPayload(payloadBytes, endpointId)
    }
}

private fun forwardBlockControl(endpointId: String, payload: MeshPayload, callback: PayloadDispatcherCallback) {
    val routePath = (payload.routePath + callback.getMyDeviceName()).distinct()
    val forwarded = payload.copy(routePath = routePath)
    val routeIds = forwarded.directedRouteNodeIds
    val index = routeIds.indexOf(callback.getMyNodeId())
    if (index >= 0 && index + 1 < routeIds.size) {
        callback.getConnectedEndpointIdByNodeId(routeIds[index + 1])?.let { nextEndpoint ->
            callback.sendPriorityPayload(nextEndpoint, ProtoBuf.encodeToByteArray(forwarded))
            return
        }
    }
    AppLogger.d("PayloadDispatcher", "Private control route unavailable; not broadcasting")
}

private fun decryptBlockControl(payload: MeshPayload): BlockControlEnvelope? {
    if (!payload.isPrivate || !payload.isEncrypted || payload.encryptedData.isNullOrBlank() || payload.encryptedKey.isNullOrBlank()) return null
    return CryptoManager.decryptHybrid(payload.encryptedData, payload.encryptedKey)
        ?.let(BlockControlEnvelope::decode)
}

class BlockRequestHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK_REQUEST"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (!payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true)) {
            forwardBlockControl(endpointId, payload, callback)
            return
        }
        val envelope = decryptBlockControl(payload) ?: return
        if (envelope.kind != BlockControlKind.REQUEST || envelope.operationId != payload.targetMessageId ||
            !NodeIdentity.matches(envelope.targetName, callback.getMyDeviceName()) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)
        ) return
        callback.onBlockRequest(endpointId, payload, envelope)
    }
}

class BlockAckHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK_ACK"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        if (!payload.targetNodeId.equals(callback.getMyNodeId(), ignoreCase = true)) {
            forwardBlockControl(endpointId, payload, callback)
            return
        }
        val envelope = decryptBlockControl(payload) ?: return
        if (envelope.kind != BlockControlKind.ACK || envelope.operationId != payload.targetMessageId ||
            !NodeIdentity.matches(envelope.targetName, callback.getMyDeviceName()) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)
        ) return
        callback.onBlockAck(endpointId, payload, envelope)
    }
}

class LegacyBlockControlHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "BLOCK" || payloadType == "UNBLOCK"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onLegacyBlockControl(payload.type, payload.senderName)
    }
}
