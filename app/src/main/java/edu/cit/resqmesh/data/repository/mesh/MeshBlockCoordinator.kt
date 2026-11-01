package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.model.BlockRelationship
import edu.cit.resqmesh.core.model.BlockRelationshipOrigin
import edu.cit.resqmesh.core.model.BlockRelationshipStatus
import edu.cit.resqmesh.core.network.BlockControlEnvelope
import edu.cit.resqmesh.core.network.BlockControlKind
import edu.cit.resqmesh.core.network.CryptoManager
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.logging.AppLogger
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
import java.util.concurrent.ConcurrentHashMap
import edu.cit.resqmesh.data.repository.BlockRelationshipStore
import edu.cit.resqmesh.data.repository.MeshRouter
import edu.cit.resqmesh.data.repository.PayloadFactory
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import edu.cit.resqmesh.data.repository.PrivateDeliveryPlanner
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_REQUEST_TYPE
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_ACK_TYPE
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_DIRECT_GRACE_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_ACK_GRACE_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_RETRY_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_RETRY_MAX_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_TEARDOWN_GRACE_MS
import edu.cit.resqmesh.data.repository.mesh.MeshDeliveryConstants.BLOCK_DELIVERY_MIN_INTERVAL_MS
import edu.cit.resqmesh.core.network.bluetooth.DirectIdentityPolicy

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
    private val clock: () -> Long = System::currentTimeMillis
    private val myNodeName get() = localName()
    private val _blockedDeviceNames get() = blockedNames()
    private fun readyConnectedDevices() = readyPeers()

    private val blockRetryJobs = ConcurrentHashMap<String, Job>()
    private val deliveryLimiter = BlockDeliveryLimiter(BLOCK_DELIVERY_MIN_INTERVAL_MS)

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
        blockStore.activeRelationships().filter { BlockRelationshipStore.isBlockable(it.peerName) }
            .forEach { relationship ->
                networkManager.denyDirectIdentity(relationship.peerName)
        }
        publishBlockedDevices()
    }

    fun resumePendingBlockRequests() {
        blockStore.activeRelationships()
            .filter { it.origin == BlockRelationshipOrigin.LOCAL && it.status == BlockRelationshipStatus.PENDING_ACK }
            .filter { BlockRelationshipStore.isBlockable(it.peerName) }
            .forEach(::startBlockRetry)
    }

    fun blockDevice(deviceName: String) {
        if (!BlockRelationshipStore.isBlockable(deviceName)) {
            AppLogger.d("BLE_MESH", "Block refused: peer has no stable identity yet")
            return
        }
        val relationship = blockStore.beginLocal(deviceName, UUID.randomUUID().toString())
        networkManager.denyDirectIdentity(relationship.peerName, BLOCK_TEARDOWN_GRACE_MS)
        publishBlockedDevices()
        if (relationship.origin == BlockRelationshipOrigin.LOCAL && relationship.status == BlockRelationshipStatus.PENDING_ACK) {
            startBlockRetry(relationship)
        }
    }

    fun unblockDevice(deviceName: String) {
        val released = blockStore.releaseLocal(deviceName)
        val peerName = released?.peerName ?: deviceName
        blockRetryJobs.remove(NodeIdentity.key(peerName))?.cancel()
        deliveryLimiter.clear(NodeIdentity.key(peerName))
        networkManager.releaseDirectIdentity(peerName)
        publishBlockedDevices()
    }

    /**
     * Best effort only: true when a route to [peerName] exists without a direct link to it, using the
     * topology currently advertised by other ready neighbours. It does not guarantee delivery.
     */
    fun hasAlternateRoute(peerName: String): Boolean {
        val others = readyConnectedDevices().filterNot { NodeIdentity.matches(it.name, peerName) }
        if (others.isEmpty()) return false
        return meshRouter.findShortestPath(myNodeName, peerName, others).isNotEmpty()
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
            var attempt = 0
            while (true) {
                delay(BlockRetryBackoff.delayMs(attempt++))
                val pending = blockStore.relationshipFor(relationship.peerName)
                if (pending?.operationId != relationship.operationId || pending.status != BlockRelationshipStatus.PENDING_ACK) break
                sendBlockRequest(pending)
            }
        }
    }

    /**
     * A blocked peer with an unacknowledged local request reached the identity gate on a fresh link. Records its
     * key only from a validated pulse, sends the sealed request straight down that endpoint (it is not published
     * yet) and reports true so the gate keeps the link open for the ACK. Rate-limited per peer.
     */
    fun onBlockedPeerPulse(endpointId: String, payload: MeshPayload): Boolean {
        val relationship = pendingLocalFor(payload) ?: return false
        return deliverPendingRequest(endpointId, payload, relationship)
    }

    private fun deliverPendingRequest(endpointId: String, payload: MeshPayload, relationship: BlockRelationship): Boolean {
        val key = NodeIdentity.key(relationship.peerName)
        if (!deliveryLimiter.isOpen(key, clock())) return false
        publicKeys.observe(payload.senderName, payload.senderNodeId, payload.publicKey)
        val bytes = buildBlockRequestBytes(relationship, listOf(myNodeName, payload.senderName))
        val sent = bytes != null && networkManager.sendPriorityPayload(endpointId, bytes).accepted
        if (sent) deliveryLimiter.record(key, clock())
        return sent
    }

    private fun pendingLocalFor(payload: MeshPayload): BlockRelationship? {
        val relationship = blockStore.relationshipFor(payload.senderName)?.takeIf {
            it.origin == BlockRelationshipOrigin.LOCAL && it.status == BlockRelationshipStatus.PENDING_ACK
        }
        return relationship?.takeIf {
            DirectIdentityPolicy.validate(payload) { Base64.decode(it, Base64.DEFAULT) } != null
        }
    }

    private fun sendBlockRequest(relationship: BlockRelationship) {
        val route = meshRouter.findShortestPath(myNodeName, relationship.peerName, readyConnectedDevices())
        val bytes = buildBlockRequestBytes(relationship, route) ?: return
        sendControl(relationship.peerName, route, bytes)
    }

    private fun buildBlockRequestBytes(relationship: BlockRelationship, route: List<String>): ByteArray? =
        sealedRetryPayload(relationship, route) ?: createSealedRequest(relationship, route)

    private fun createSealedRequest(relationship: BlockRelationship, route: List<String>): ByteArray? {
        val targetKey = publicKeys.trustedKey(relationship.peerName)
        if (targetKey.isNullOrBlank()) {
            AppLogger.d("MeshNetwork_E2EE", "Block request pending: no recipient key for ${relationship.peerName}")
            return null
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
        }.onFailure {
            AppLogger.d("MeshNetwork_E2EE", "Block request encryption failed for ${relationship.peerName}")
        }.getOrNull()
        created?.let {
            blockStore.setSealedRequest(
                relationship.peerName,
                relationship.operationId,
                Base64.encodeToString(it, Base64.NO_WRAP)
            )
        }
        return created
    }

    private fun sealedRetryPayload(relationship: BlockRelationship, route: List<String>): ByteArray? {
        if (relationship.sealedRequest.isBlank()) return null
        return runCatching {
            val original = ProtoBuf.decodeFromByteArray<edu.cit.resqmesh.core.network.MeshPayload>(
                Base64.decode(relationship.sealedRequest, Base64.NO_WRAP)
            )
            ProtoBuf.encodeToByteArray(original.copy(
                id = UUID.randomUUID().toString(),
                directedRoute = route
            ))
        }.getOrNull()
    }

    private fun handleBlockRequest(endpointId: String, payload: edu.cit.resqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
        if (!NodeIdentity.matches(envelope.targetName, myNodeName) || !NodeIdentity.matches(envelope.initiatorName, payload.senderName)) return
        val relationship = blockStore.acceptRemote(
            peerName = envelope.initiatorName,
            operationId = envelope.operationId,
            replyPublicKey = envelope.replyPublicKey
        )
        publishBlockedDevices()
        if (relationship.deniesDirectLink) {
            networkManager.denyDirectIdentity(relationship.peerName, BLOCK_TEARDOWN_GRACE_MS)
        }
        sendBlockAck(payload, envelope)
        repositoryScope.launch {
            delay(BLOCK_ACK_GRACE_MS)
            val current = blockStore.relationshipFor(envelope.initiatorName)
            if (current?.deniesDirectLink == true) {
                networkManager.disconnectDirectIdentity(envelope.initiatorName, "block request acknowledged")
            }
        }
    }

    private fun sendBlockAck(request: edu.cit.resqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
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

    private fun handleBlockAck(endpointId: String, payload: edu.cit.resqmesh.core.network.MeshPayload, envelope: BlockControlEnvelope) {
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

/** Block request retry delay: 3 s doubling to a 60 s cap. */
internal object BlockRetryBackoff {
    private const val MAX_SHIFT = 16

    fun delayMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, MAX_SHIFT)
        return (BLOCK_RETRY_MS shl shift).coerceAtMost(BLOCK_RETRY_MAX_MS)
    }
}

/** Allows one direct request delivery per peer key per interval. */
internal class BlockDeliveryLimiter(private val minIntervalMs: Long) {
    private val last = ConcurrentHashMap<String, Long>()

    fun isOpen(key: String, now: Long): Boolean = last[key]?.let { now - it >= minIntervalMs } ?: true

    /** Called only after a delivery was accepted, so a failed attempt never costs the slot. */
    fun record(key: String, now: Long) { last[key] = now }

    fun clear(key: String) { last.remove(key) }
}
