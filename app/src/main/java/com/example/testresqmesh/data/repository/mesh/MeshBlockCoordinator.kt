package com.example.testresqmesh.data.repository.mesh

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.BlockRelationship
import com.example.testresqmesh.core.model.BlockRelationshipOrigin
import com.example.testresqmesh.core.model.BlockRelationshipStatus
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.BlockControlKind
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import android.util.Base64
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.UUID
import com.example.testresqmesh.data.repository.BlockRelationshipStore
import com.example.testresqmesh.data.repository.MeshRouter
import com.example.testresqmesh.data.repository.PayloadFactory
import com.example.testresqmesh.data.repository.PeerPublicKeyDirectory
import com.example.testresqmesh.data.repository.PrivateDeliveryPlanner
import com.example.testresqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_REQUEST_TYPE
import com.example.testresqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_ACK_TYPE
import com.example.testresqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_DIRECT_GRACE_MS
import com.example.testresqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_ACK_GRACE_MS
import com.example.testresqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_RETRY_MS

internal class MeshBlockCoordinator(
    private val networkManager: MeshNetworkGateway,
    private val blockStore: BlockRelationshipStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope,
    private val meshRouter: MeshRouter,
    private val localName: () -> String,
    private val blockedNames: () -> MutableStateFlow<Set<String>>,
    private val readyPeers: () -> List<ConnectedDevice>
) {
    private val myNodeName get() = localName()
    private val _blockedDeviceNames get() = blockedNames()
    private fun readyConnectedDevices() = readyPeers()

    private val blockRetryJobs = mutableMapOf<String, Job>()

    fun onNetworkDeviceBlocked(senderName: String): Unit = run {
        AppLogger.d("BLE_MESH", "Ignoring legacy block callback from $senderName")
    }

    fun onNetworkDeviceUnblocked(senderName: String): Unit = run {
        AppLogger.d("BLE_MESH", "Ignoring legacy unblock callback from $senderName")
    }

    fun onNetworkBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope): Unit = run {
        handleBlockRequest(endpointId, payload, envelope)
    }

    fun onNetworkBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope): Unit = run {
        handleBlockAck(endpointId, payload, envelope)
    }

    private fun publishBlockedDevices() {
        _blockedDeviceNames.value = blockStore.activeRelationships().map { it.peerName }.toSet()
    }

    fun synchronizeBlockRelationships() {
        blockStore.activeRelationships().forEach { relationship ->
            networkManager.denyDirectIdentity(relationship.peerName)
        }
        publishBlockedDevices()
    }

    fun resumePendingBlockRequests() {
        blockStore.activeRelationships()
            .filter { it.origin == BlockRelationshipOrigin.LOCAL && it.status == BlockRelationshipStatus.PENDING_ACK }
            .forEach(::startBlockRetry)
    }

    fun blockDevice(deviceName: String) {
        val relationship = blockStore.beginLocal(deviceName, UUID.randomUUID().toString())
        networkManager.denyDirectIdentity(relationship.peerName)
        publishBlockedDevices()
        if (relationship.origin == BlockRelationshipOrigin.LOCAL && relationship.status == BlockRelationshipStatus.PENDING_ACK) {
            startBlockRetry(relationship)
        }
    }

    fun unblockDevice(deviceName: String) {
        val released = blockStore.releaseLocal(deviceName)
        val peerName = released?.peerName ?: deviceName
        blockRetryJobs.remove(NodeIdentity.key(peerName))?.cancel()
        networkManager.releaseDirectIdentity(peerName)
        publishBlockedDevices()
    }

    private fun startBlockRetry(relationship: BlockRelationship) {
        val key = NodeIdentity.key(relationship.peerName)
        if (blockRetryJobs[key]?.isActive == true) return
        sendBlockRequest(relationship)
        blockRetryJobs[key] = repositoryScope.launch {
            delay(BLOCK_DIRECT_GRACE_MS)
            val current = blockStore.relationshipFor(relationship.peerName)
            if (current?.operationId == relationship.operationId && current.status == BlockRelationshipStatus.PENDING_ACK) {
                networkManager.disconnectDirectIdentity(relationship.peerName, "block request grace elapsed")
            }
            while (true) {
                delay(BLOCK_RETRY_MS)
                val pending = blockStore.relationshipFor(relationship.peerName)
                if (pending?.operationId != relationship.operationId || pending.status != BlockRelationshipStatus.PENDING_ACK) break
                sendBlockRequest(pending)
            }
        }
    }

    private fun sendBlockRequest(relationship: BlockRelationship) {
        val route = meshRouter.findShortestPath(myNodeName, relationship.peerName, readyConnectedDevices())
        val bytes = sealedRetryPayload(relationship, route) ?: run {
            val targetKey = publicKeys.trustedKey(relationship.peerName)
            if (targetKey.isNullOrBlank()) {
                AppLogger.d("MeshNetwork_E2EE", "Block request pending: no recipient key for ${relationship.peerName}")
                return
            }
            val envelope = BlockControlEnvelope(
                kind = BlockControlKind.REQUEST,
                operationId = relationship.operationId,
                initiatorName = myNodeName,
                targetName = relationship.peerName,
                replyPublicKey = CryptoManager.getMyPublicKeyBase64()
            )
            val created = runCatching {
                PayloadFactory.buildBlockControlPayload(
                    transmissionId = UUID.randomUUID().toString(),
                    payloadType = BLOCK_REQUEST_TYPE,
                    operationId = relationship.operationId,
                    senderName = myNodeName,
                    targetName = relationship.peerName,
                    directedRoute = route,
                    targetPublicKey = targetKey,
                    envelopeJson = envelope.encode()
                )
            }.getOrElse {
                AppLogger.d("MeshNetwork_E2EE", "Block request encryption failed for ${relationship.peerName}")
                return
            }
            blockStore.setSealedRequest(
                relationship.peerName,
                relationship.operationId,
                Base64.encodeToString(created, Base64.NO_WRAP)
            )
            created
        }
        sendControl(relationship.peerName, route, bytes)
    }

    private fun sealedRetryPayload(relationship: BlockRelationship, route: List<String>): ByteArray? {
        if (relationship.sealedRequest.isBlank()) return null
        return runCatching {
            val original = ProtoBuf.decodeFromByteArray<com.example.testresqmesh.core.network.MeshPayload>(
                Base64.decode(relationship.sealedRequest, Base64.NO_WRAP)
            )
            ProtoBuf.encodeToByteArray(original.copy(
                id = UUID.randomUUID().toString(),
                directedRoute = route
            ))
        }.getOrNull()
    }

    private fun handleBlockRequest(endpointId: String, payload: com.example.testresqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
        if (!NodeIdentity.matches(envelope.targetName, myNodeName) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)) return
        val relationship = blockStore.acceptRemote(
            peerName = envelope.initiatorName,
            operationId = envelope.operationId,
            replyPublicKey = envelope.replyPublicKey
        )
        publishBlockedDevices()
        if (relationship.deniesDirectLink) networkManager.denyDirectIdentity(relationship.peerName)
        sendBlockAck(payload, envelope)
        repositoryScope.launch {
            delay(BLOCK_ACK_GRACE_MS)
            val current = blockStore.relationshipFor(envelope.initiatorName)
            if (current?.deniesDirectLink == true) {
                networkManager.disconnectDirectIdentity(envelope.initiatorName, "block request acknowledged")
            }
        }
    }

    private fun sendBlockAck(request: com.example.testresqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
        val replyPublicKey = envelope.replyPublicKey.ifBlank {
            blockStore.relationshipFor(envelope.initiatorName)?.replyPublicKey.orEmpty()
        }
        if (replyPublicKey.isBlank()) {
            AppLogger.d("MeshNetwork_E2EE", "Cannot acknowledge block request: reply key missing")
            return
        }
        val reverseRoute = request.directedRoute.takeIf { it.isNotEmpty() }?.reversed()
            ?: (listOf(myNodeName) + request.routePath.asReversed()).distinct()
        val ack = BlockControlEnvelope(
            kind = BlockControlKind.ACK,
            operationId = envelope.operationId,
            initiatorName = myNodeName,
            targetName = envelope.initiatorName
        )
        val bytes = runCatching {
            PayloadFactory.buildBlockControlPayload(
                transmissionId = UUID.randomUUID().toString(),
                payloadType = BLOCK_ACK_TYPE,
                operationId = envelope.operationId,
                senderName = myNodeName,
                targetName = envelope.initiatorName,
                directedRoute = reverseRoute,
                targetPublicKey = replyPublicKey,
                envelopeJson = ack.encode()
            )
        }.getOrElse {
            AppLogger.d("MeshNetwork_E2EE", "Block acknowledgement encryption failed")
            return
        }
        sendControl(envelope.initiatorName, reverseRoute, bytes)
    }

    private fun handleBlockAck(endpointId: String, payload: com.example.testresqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
        if (!NodeIdentity.matches(envelope.targetName, myNodeName) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)) return
        if (!blockStore.confirmLocalAck(payload.senderName, envelope.operationId)) return
        publishBlockedDevices()
        blockRetryJobs.remove(NodeIdentity.key(payload.senderName))?.cancel()
        networkManager.disconnectDirectIdentity(payload.senderName, "block acknowledgement received")
    }

    private fun sendControl(targetName: String, route: List<String>, payloadBytes: ByteArray) {
        when (val target = PrivateDeliveryPlanner.select(targetName, route, readyConnectedDevices())) {
            is PrivateDeliveryPlanner.Target.Endpoint -> networkManager.sendPriorityPayload(target.endpointId, payloadBytes)
            PrivateDeliveryPlanner.Target.Unavailable ->
                AppLogger.d("MeshNetwork_E2EE", "Control message route unavailable; not broadcasting")
        }
    }
}
