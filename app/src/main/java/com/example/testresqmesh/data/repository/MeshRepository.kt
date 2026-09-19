package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.BlockRelationship
import com.example.testresqmesh.core.model.BlockRelationshipOrigin
import com.example.testresqmesh.core.model.BlockRelationshipStatus
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.BlockControlKind
import com.example.testresqmesh.core.network.CryptoManager
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import android.util.Base64
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.UUID

class MeshRepository(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val blockStore: BlockRelationshipStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope
) {

    private val _connectionStatus = MutableStateFlow("Ready to deploy Mesh Node.")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _currentChannelId = MutableStateFlow("1")
    val currentChannelId: StateFlow<String> = _currentChannelId.asStateFlow()

    fun setChannel(channelId: String) {
        _currentChannelId.value = channelId
    }

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()
    private fun readyConnectedDevices(): List<ConnectedDevice> = _connectedDevices.value.filter {
        it.isPayloadReady && !NodeIdentity.isPlaceholder(it.name)
    }

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _incomingSosAlert = MutableStateFlow<ChatMessage?>(null)
    val incomingSosAlert: StateFlow<ChatMessage?> = _incomingSosAlert.asStateFlow()

    val incomingVoiceMessage = kotlinx.coroutines.flow.MutableSharedFlow<ChatMessage>(extraBufferCapacity = 10)
    val incomingLiveAudioChunk = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 100)

    fun clearSosAlert() {
        _incomingSosAlert.value = null
    }

    private val meshRouter = MeshRouter()
    val knownNodes = meshRouter.knownNodes
    val topology = meshRouter.topology

    val publicMessages = messageStore.publicMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val privateMessages = messageStore.privateMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""
    val blockRelationships = blockStore.relationships
    private val blockRetryJobs = mutableMapOf<String, Job>()

    init {
        setupCallbacks()
        meshRouter.startTopologyCleanup(repositoryScope, { myNodeName }, { readyConnectedDevices() })
    }

    /**
     * Identity comparison for two links. Falls back to the stable node ID so a provisional socket
     * (whose name is still a placeholder, and which therefore matches nothing by name) is still
     * recognised as the same peer.
     */
    private fun sameNode(a: ConnectedDevice, b: ConnectedDevice): Boolean {
        if (a.nodeId.isNotEmpty() && b.nodeId.isNotEmpty()) return a.nodeId == b.nodeId
        return NodeIdentity.matches(a.name, b.name)
    }

    private fun setupCallbacks() {        networkManager.onStatusChanged = { status ->
            _connectionStatus.value = status
        }

        networkManager.onDeviceConnected = { device ->
            val existingById = _connectedDevices.value.find { it.endpointId == device.endpointId }
            val existingByIdentity = _connectedDevices.value.find {
                it.endpointId != device.endpointId && sameNode(it, device)
            }
            val existingIsReady = existingByIdentity?.let { networkManager.hasReadyEndpoint(it.endpointId) } ?: false
            if (existingById == null && !NodeIdentity.isPlaceholder(device.name) &&
                (existingByIdentity == null || !networkManager.hasLiveSocket(existingByIdentity.endpointId))) {
            }

            var updatedList = _connectedDevices.value
            var duplicateRejected = false

            // DUPLICATE ENDPOINT RESOLUTION.
            // This used to unconditionally disconnect the older endpoint the moment an identity
            // match appeared, which meant the app tore down its own healthy socket every time
            // dual-MAC produced a second entry for one peer: connect -> "ghost socket" ->
            // self-disconnect -> rescan -> reconnect. Now a stale row is merged away without
            // touching the radio, and when two sockets really are live the survivor is chosen
            // deterministically by age so the churn cannot keep winning.
            if (existingByIdentity != null && !NodeIdentity.isPlaceholder(device.name)) {
                val existingIsLive = networkManager.hasLiveSocket(existingByIdentity.endpointId)
                val incomingIsLive = networkManager.hasLiveSocket(device.endpointId)
                val incomingIsReady = networkManager.hasReadyEndpoint(device.endpointId)

                when {
                    !existingIsLive -> {
                        AppLogger.d("BLE_MESH", "Merging stale endpoint ${existingByIdentity.endpointId} into ${device.endpointId} for ${device.name}")
                        updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                        meshRouter.removeNode(existingByIdentity.name)
                    }
                    !incomingIsLive -> {
                        AppLogger.d("BLE_MESH", "Ignoring dead duplicate endpoint ${device.endpointId} for ${device.name}")
                        duplicateRejected = true
                    }
                    existingIsReady && !incomingIsReady -> {
                        AppLogger.d("BLE_MESH", "Keeping READY endpoint ${existingByIdentity.endpointId} for ${device.name}; alternate endpoint is still configuring")
                        duplicateRejected = true
                    }
                    incomingIsReady && !existingIsReady -> {
                        AppLogger.d("BLE_MESH", "Selecting READY endpoint ${device.endpointId} for ${device.name}; retaining alternate radio role")
                        updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                    }
                    networkManager.linkEstablishedAt(existingByIdentity.endpointId) <= networkManager.linkEstablishedAt(device.endpointId) -> {
                        AppLogger.d("BLE_MESH", "Duplicate link to ${device.name}. Keeping older endpoint ${existingByIdentity.endpointId} in routing view.")
                        duplicateRejected = true
                    }
                    else -> {
                        AppLogger.d("BLE_MESH", "Duplicate link to ${device.name}. Keeping newer endpoint ${device.endpointId} in routing view.")
                        updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                    }
                }
            }

            updatedList = when {
                // The surviving entry still adopts the freshly resolved name and identity.
                duplicateRejected && existingByIdentity != null -> updatedList.map {
                    if (it.endpointId == existingByIdentity.endpointId) {
                        it.copy(
                            name = device.name,
                            isProvisional = false,
                            nodeId = device.nodeId.ifEmpty { it.nodeId },
                            isPayloadReady = existingIsReady
                        )
                    } else it
                }
                duplicateRejected -> updatedList
                existingById != null -> {
                    if (existingById.name != device.name) {
                        meshRouter.removeNode(existingById.name)
                    }
                    updatedList.map { if (it.endpointId == device.endpointId) device else it }
                }
                else -> updatedList + device
            }

            // Always purge the discovery list for this peer, including on the rename path. Previously
            // only brand new devices purged it, so a peer first seen as a nameless inbound socket left
            // a stale scanned row behind on its advertising MAC. That row is what the Radar rendered
            // as "Connected (Via Relay)" for an already directly connected device.
            _scannedDevices.value = _scannedDevices.value.filter {
                it.endpointId != device.endpointId &&
                    !NodeIdentity.matches(it.name, device.name) &&
                    !(device.nodeId.isNotEmpty() && it.nodeId == device.nodeId)
            }

            // Provisional links have a real socket but only a placeholder name, so they must not be
            // published into the routing tables or they would pollute the topology with ghost nodes.
            if (device.isPayloadReady && !device.isProvisional && !NodeIdentity.isPlaceholder(device.name)) {
                meshRouter.markNodeSeen(device.name)
            }

            _connectedDevices.value = updatedList
            meshRouter.recalculateKnownNodes(myNodeName, updatedList.filter { it.isPayloadReady })
        }

        networkManager.onDeviceDisconnected = { endpointId ->
            val disconnectedDevice = _connectedDevices.value.find { it.endpointId == endpointId }
            val peerStillReady = disconnectedDevice != null && !NodeIdentity.isPlaceholder(disconnectedDevice.name) &&
                networkManager.hasReadyLinkToIdentity(disconnectedDevice.name)
            if (disconnectedDevice != null && !NodeIdentity.isPlaceholder(disconnectedDevice.name) && !peerStillReady) {
            }
            _connectedDevices.value = _connectedDevices.value.filter {
                it.endpointId != endpointId || peerStillReady && networkManager.hasLiveSocket(endpointId)
            }
            if (disconnectedDevice != null && !peerStillReady) {
                meshRouter.removeNode(disconnectedDevice.name)
            }
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
        }

        networkManager.onDeviceLivenessChanged = { endpointId, responsive ->
            val current = _connectedDevices.value
            if (current.any { it.endpointId == endpointId && it.isPeerResponsive != responsive }) {
                _connectedDevices.value = current.map { device ->
                    if (device.endpointId == endpointId) device.copy(isPeerResponsive = responsive) else device
                }
            }
        }
        
        networkManager.onPublicKeyReceived = { senderName, senderNodeId, key ->
            when (publicKeys.observe(senderName, senderNodeId, key)) {
                PeerPublicKeyDirectory.Observation.KEY_CHANGE_PENDING ->
                    AppLogger.d("BLE_MESH", "Public-key change requires approval for $senderName")
                PeerPublicKeyDirectory.Observation.INVALID_IDENTITY ->
                    AppLogger.d("BLE_MESH", "Ignored public key with invalid stable identity")
                else -> AppLogger.d("BLE_MESH", "Recorded public-key observation for $senderName")
            }
        }
        
        networkManager.onRoutingTableReceived = { senderName, senderNodeId, connectedNodes, connectedNodeIds ->
            meshRouter.updateTopology(senderName, senderNodeId, connectedNodes, connectedNodeIds, myNodeName)
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
            AppLogger.event(
                category = com.example.testresqmesh.core.utils.TerminalLogCategory.ROUTING,
                event = "TOPOLOGY_UPDATED",
                message = "Applied topology from peer; ${connectedNodes.size} advertised neighbor(s)",
                peerName = senderName
            )
        }

        networkManager.onDeviceBlocked = { senderName ->
            AppLogger.d("BLE_MESH", "Ignoring legacy block callback from $senderName")
        }

        networkManager.onDeviceUnblocked = { senderName ->
            AppLogger.d("BLE_MESH", "Ignoring legacy unblock callback from $senderName")
        }

        networkManager.onBlockRequest = { endpointId, payload, envelope ->
            handleBlockRequest(endpointId, payload, envelope)
        }

        networkManager.onBlockAck = { endpointId, payload, envelope ->
            handleBlockAck(endpointId, payload, envelope)
        }

        networkManager.checkRouteExists = { targetName ->
            meshRouter.knownNodes.value.any { NodeIdentity.matches(it.name, targetName) }
        }

        networkManager.onDeviceScanned = { event ->
            if (!NodeIdentity.matches(event.name, myNodeName)) {
                // A peer we already hold a physical socket to must never appear in the discovery list.
                // Node ID is checked too, so a still-provisional link (placeholder name, matches
                // nothing by name) also suppresses its own advertising MAC.
                val isPhysicallyConnected = _connectedDevices.value.any {
                    it.endpointId == event.endpointId ||
                        NodeIdentity.matches(it.name, event.name) ||
                        (event.nodeId.isNotEmpty() && it.nodeId == event.nodeId)
                }

                if (isPhysicallyConnected) {
                    _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != event.endpointId }
                } else {
                    val currentScanned = _scannedDevices.value.toMutableList()
                    val existingIndex = currentScanned.indexOfFirst {
                        it.endpointId == event.endpointId || NodeIdentity.matches(it.name, event.name)
                    }

                    if (existingIndex != -1) {
                        val existing = currentScanned[existingIndex]
                        currentScanned[existingIndex] = existing.copy(
                            endpointId = event.endpointId,
                            name = event.name.ifBlank { existing.name },
                            lastSeen = System.currentTimeMillis(),
                            powerScore = event.peerConnections ?: existing.powerScore,
                            myRole = event.peerScore ?: existing.myRole,
                            isConnecting = event.isConnecting,
                            nodeId = event.nodeId.ifEmpty { existing.nodeId }
                        )
                        _scannedDevices.value = currentScanned
                    } else if (event.name.isNotBlank()) {
                        currentScanned.add(
                            ScannedDevice(
                                endpointId = event.endpointId,
                                name = event.name,
                                lastSeen = System.currentTimeMillis(),
                                powerScore = event.peerConnections ?: 0,
                                myRole = event.peerScore ?: "IDLE",
                                isConnecting = event.isConnecting,
                                nodeId = event.nodeId
                            )
                        )
                        _scannedDevices.value = currentScanned
                    }
                }
            }
        }

        networkManager.onDeviceScanRemoved = { id ->
            _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != id }
        }

        networkManager.onSosCancelled = {
            clearSosAlert()
        }

        networkManager.stpNeighborsProvider = {
            meshRouter.getSpanningTreeNeighbors(myNodeName, readyConnectedDevices())
        }

        networkManager.onLiveAudioChunk = { sender, channelId, chunk ->
            if (channelId == _currentChannelId.value) {
                repositoryScope.launch {
                    incomingLiveAudioChunk.emit(Pair(sender, chunk))
                }
            }
        }

        networkManager.onMessageReceived = { endpointId, msgId, sender, text, isPrivate, isSystem, img, audio, lat, lng, medium, routePath, channelId ->
            if (sender != myNodeName) {
                meshRouter.markNodeSeen(sender)
                meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())

                if (!isSystem && (isPrivate || channelId == _currentChannelId.value)) {
                    val isDirect = _connectedDevices.value.any { NodeIdentity.matches(it.name, sender) }
                    val message = ChatMessage(
                        id = msgId,
                        senderName = sender,
                        text = text,
                        imageBase64 = img,
                        audioBase64 = audio,
                        locationLat = lat,
                        locationLng = lng,
                        isMine = false,
                        isPrivate = isPrivate,
                        timestamp = System.currentTimeMillis(),
                        isHopped = !isDirect,
                        receiveMedium = medium,
                        outboundRoute = routePath,
                        isSOS = text.contains("🚨 CRITICAL SOS")
                    )
                    if (isPrivate) {
                        repositoryScope.launch {
                            messageStore.save(message, targetName = sender)
                        }
                        
                        val reversedRoute = routePath.reversed().toMutableList()
                        if (reversedRoute.isNotEmpty() && reversedRoute.first() != myNodeName) {
                            reversedRoute.add(0, myNodeName)
                        }
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute)
                    } else {
                        repositoryScope.launch {
                            messageStore.save(message, targetName = null)
                        }
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
                        
                        if (message.isSOS) {
                            _incomingSosAlert.value = message
                        }
                    }

                    if (message.audioBase64 != null) {
                        incomingVoiceMessage.tryEmit(message)
                    }
                }
            }
        }
        networkManager.onMessageDelivered = { msgId, readerName, returnRoute ->
            val currentSos = _incomingSosAlert.value
            if (currentSos?.id == msgId && !currentSos.deliveredTo.contains(readerName)) {
                _incomingSosAlert.value = currentSos.copy(deliveredTo = currentSos.deliveredTo + readerName)
            }

            repositoryScope.launch {
                messageStore.markDelivered(msgId, readerName)
            }
        }
        
        networkManager.onMessageSeen = { msgId, readerName ->
            repositoryScope.launch {
                messageStore.markSeen(msgId, readerName)
            }
        }
    }

    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null) {
        repositoryScope.launch {
            messageStore.markSeen(messageId, "Me")
        }
        
        val directEndpointId = _connectedDevices.value.find { NodeIdentity.matches(it.name, targetName) }?.endpointId
        if (directEndpointId != null) {
            networkManager.broadcastSeenReceipt(messageId, isPrivate, directEndpointId)
        } else {
            networkManager.broadcastSeenReceipt(messageId, isPrivate, null)
        }
    }

    fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        myNodeName = "$customName [$nodeTag]#$nodeId"
        networkManager.myDeviceName = myNodeName
        networkManager.myNodeId = nodeId
        synchronizeBlockRelationships()
        networkManager.startMeshNode(teamKey)
        resumePendingBlockRequests()
        _isOnline.value = true
    }

    fun stopNode() {
        networkManager.stopMeshNode()
        _isOnline.value = false
        _connectedDevices.value = emptyList()
        _scannedDevices.value = emptyList()
    }

    fun disconnectDevice(endpointId: String) {
        networkManager.disconnectFromEndpoint(endpointId)
    }
    
    private val _blockedDeviceNames = MutableStateFlow<Set<String>>(emptySet())
    val blockedDeviceNames: StateFlow<Set<String>> = _blockedDeviceNames.asStateFlow()

    private fun publishBlockedDevices() {
        _blockedDeviceNames.value = blockStore.activeRelationships().map { it.peerName }.toSet()
    }

    private fun synchronizeBlockRelationships() {
        blockStore.activeRelationships().forEach { relationship ->
            networkManager.denyDirectIdentity(relationship.peerName)
        }
        publishBlockedDevices()
    }

    private fun resumePendingBlockRequests() {
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

    private companion object {
        const val BLOCK_REQUEST_TYPE = "BLOCK_REQUEST"
        const val BLOCK_ACK_TYPE = "BLOCK_ACK"
        const val BLOCK_DIRECT_GRACE_MS = 2_000L
        const val BLOCK_ACK_GRACE_MS = 1_000L
        const val BLOCK_RETRY_MS = 3_000L
    }

    @Synchronized
    fun broadcastLiveAudioChunk(chunk: ByteArray) {
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "LIVE_AUDIO",
            senderName = myNodeName,
            channelId = _currentChannelId.value,
            liveAudioChunk = chunk
        )
        val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
        
        // STP Directed Routing: Only initiate stream to Spanning Tree neighbors
        val stpNeighbors = meshRouter.getSpanningTreeNeighbors(myNodeName, readyConnectedDevices())
        
        // Better yet: just send to connected devices whose name is in stpNeighbors
        _connectedDevices.value.forEach { device ->
            if (NodeIdentity.matchesAny(device.name, stpNeighbors)) {
                networkManager.sendDirectPayload(device.endpointId, payloadBytes)
            }
        }
    }

    fun forceConnect(endpointId: String, endpointName: String) {
        networkManager.forceConnectToDevice(endpointId, endpointName)
    }

    fun rescan() {
        networkManager.rescan()
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false): String {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val payloadBytes = PayloadFactory.buildPublicPayload(
            msgId = messageId,
            timestamp = timestamp,
            senderName = myNodeName,
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel,
            channelId = _currentChannelId.value
        )

        val message = ChatMessage(messageId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp, isSOS = isSOS)
        repositoryScope.launch {
            messageStore.save(message, targetName = null)
        }
        if (isSOS) networkManager.broadcastPriorityPayload(payloadBytes) else networkManager.broadcastPayload(payloadBytes)
        return messageId
    }

    fun deleteConversationWith(peerName: String) {
        repositoryScope.launch {
            messageStore.deleteConversation(peerName)
        }
    }

    fun hasPendingPublicKeyChange(peerName: String): Boolean = publicKeys.hasPendingChange(peerName)

    fun acceptPendingPublicKeyChange(peerName: String): Boolean = publicKeys.acceptPendingChange(peerName)

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null): Boolean {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val readyDevices = readyConnectedDevices()
        if (readyDevices.isEmpty()) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: no payload-ready peer link")
            return false
        }
        val directedRouteList = meshRouter.findShortestPath(myNodeName, targetName, readyDevices)
        if (publicKeys.hasPendingChange(targetName)) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: recipient key change requires approval")
            return false
        }
        val targetPubKey = publicKeys.trustedKey(targetName)
        if (targetPubKey == null) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: no recipient public key for $targetName")
            return false
        }
        if (directedRouteList.isEmpty()) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: no stable directed route to $targetName")
            return false
        }

        val payloadBytes = runCatching { PayloadFactory.buildPrivatePayload(
            msgId = msgId,
            timestamp = timestamp,
            senderName = myNodeName,
            targetName = targetName,
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            directedRoute = directedRouteList,
            targetPubKey = targetPubKey,
            channelId = _currentChannelId.value
        ) }.getOrElse {
            AppLogger.d("MeshNetwork_E2EE", "Private message to $targetName was not sent: recipient key unavailable or encryption failed")
            return false
        }

        val delivery = PrivateDeliveryPlanner.select(targetName, directedRouteList, readyDevices)
        if (delivery == PrivateDeliveryPlanner.Target.Unavailable) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: selected route has no payload-ready next hop")
            return false
        }

        val isDirect = readyDevices.any { NodeIdentity.matches(it.name, targetName) }
        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        
        repositoryScope.launch {
            messageStore.save(message, targetName = targetName)
        }

        when (delivery) {
            is PrivateDeliveryPlanner.Target.Endpoint -> {
                AppLogger.d("MeshNetwork_E2EE", "Private route selected with ${directedRouteList.size - 1} hop(s)")
                networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
            }
            PrivateDeliveryPlanner.Target.Unavailable -> error("Checked before saving the outbound message")
        }
        return true
    }
}
