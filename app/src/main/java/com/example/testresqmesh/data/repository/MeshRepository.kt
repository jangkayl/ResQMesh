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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.util.UUID

class MeshRepository(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val blockStore: BlockRelationshipStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope,
    private val readyPeerEvents: MeshReadyPeerEvents,
    private val peerNameStore: PeerNameStore,
    private val sosRepository: SosRepository? = null
) {

    private val _connectionStatus = MutableStateFlow("Ready to deploy Mesh Node.")
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _transportState = MutableStateFlow(com.example.testresqmesh.core.network.bluetooth.MeshTransportState.OFFLINE)
    val transportState = _transportState.asStateFlow()
    fun reconcileTransport() = networkManager.reconcileTransport()

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
    private val _publicSendFeedback = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    val publicSendFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _publicSendFeedback
    val incomingLiveAudioChunk = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 100)

    fun clearSosAlert() {
        _incomingSosAlert.value = null
    }

    private val meshRouter = MeshRouter()
    val knownNodes = meshRouter.knownNodes
    val topology = meshRouter.topology

    val publicMessages = messageStore.publicMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())
    val allPublicMessages = messageStore.allPublicMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val privateMessages = messageStore.privateMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    private val recentPeerNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerNames = combine(peerNameStore.names, recentPeerNames) { saved, recent -> saved + recent }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())
    private val observedPeerNames = Channel<String>(64, BufferOverflow.DROP_OLDEST)

    private fun recordPeerName(name: String, fromReadyLink: Boolean = false) {
        val id = NodeIdentity.idOf(name) ?: return
        if (id == NodeIdentity.idOf(myNodeName)) return
        if (NodeIdentity.isPlaceholder(name) || name.substringBeforeLast('#').isBlank()) return
        if (!fromReadyLink && readyConnectedDevices().any { NodeIdentity.idOf(it.name) == id && it.name != name }) return
        if (recentPeerNames.value[id] == name) return
        recentPeerNames.update { it + (id to name) }
        observedPeerNames.trySend(name)
    }

    private fun currentPeerName(name: String): String {
        val id = NodeIdentity.idOf(name) ?: return name
        return recentPeerNames.value[id]
            ?: peerNames.value[id]
            ?: readyConnectedDevices().firstOrNull { NodeIdentity.idOf(it.name) == id }?.name
            ?: name
    }

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""
    val blockRelationships = blockStore.relationships
    private val blockRetryJobs = mutableMapOf<String, Job>()
    private val outboxMutex = Mutex()
    private val outboxWake = Channel<Unit>(Channel.CONFLATED)
    private var outboxRetryJob: Job? = null
    private val privateAcceptedAttempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val privateTimeoutOwners = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val privateProgress = java.util.concurrent.ConcurrentHashMap<String, PrivateTransferProgress>()

    init {
        setupCallbacks()
        repositoryScope.launch {
            for (name in observedPeerNames) {
                try {
                    peerNameStore.observeFullName(name)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AppLogger.d("PeerNames", "Unable to persist a peer display name")
                }
            }
        }
        meshRouter.startTopologyCleanup(repositoryScope, { myNodeName }, { readyConnectedDevices() })
        repositoryScope.launch {
            outboxMutex.withLock { messageStore.recoverUnacknowledgedPrivateSends() }
            for (wake in outboxWake) flushOutbox()
        }
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
        networkManager.onOutboundFrame = { event ->
            if (event.isPrivate) repositoryScope.launch {
                outboxMutex.withLock {
                    // A recovered journal can resume before the repository has registered a send.
                    if (!privateTimeoutOwners.containsKey(event.messageId) && messageStore.isOutstandingPrivateSend(event.messageId)) {
                        privateProgress.putIfAbsent(event.messageId, PrivateTransferProgress(event.transmissionId, event.hops)
                            .observe(event, System.nanoTime() / 1_000_000))
                        messageStore.markSent(event.messageId)
                        awaitPrivateReceipt(event.messageId)
                    }
                    privateProgress.computeIfPresent(event.messageId) { _, progress ->
                        progress.observe(event, System.nanoTime() / 1_000_000)
                    }
                }
            }
        }

        networkManager.onTransportStateChanged = { state ->
            _transportState.value = state
            if (state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.OFFLINE ||
                state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.BLUETOOTH_OFF ||
                state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.PERMISSION_REQUIRED ||
                state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.ERROR) {
                _connectedDevices.value = emptyList()
                _scannedDevices.value = emptyList()
                readyPeerEvents.update(emptyList())
                meshRouter.recalculateKnownNodes(myNodeName, emptyList())
            }
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
                val preferredEndpoint = NodeIdentity.idOf(device.name)?.let(networkManager::preferredEndpointForPeer)

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
                    preferredEndpoint == device.endpointId && preferredEndpoint != existingByIdentity.endpointId -> {
                        updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                    }
                    preferredEndpoint == existingByIdentity.endpointId -> duplicateRejected = true
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
                recordPeerName(device.name, fromReadyLink = true)
            }

            _connectedDevices.value = updatedList
            readyPeerEvents.update(updatedList.filterNot { networkManager.isDeviceBlocked(it.name) })
            meshRouter.recalculateKnownNodes(myNodeName, updatedList.filter { it.isPayloadReady })
            if (device.isPayloadReady) {
                scheduleOutboxFlush()
                networkManager.wakePrivateReceipts()
                readyPeerEvents.publish(device)
            }
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
                meshRouter.onDirectPeerLost(myNodeName, disconnectedDevice.name, readyConnectedDevices())
            }
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
            readyPeerEvents.update(readyConnectedDevices().filterNot { networkManager.isDeviceBlocked(it.name) })
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
                else -> {
                    AppLogger.d("BLE_MESH", "Recorded public-key observation for $senderName")
                    scheduleOutboxFlush()
                }
            }
        }
        
        networkManager.onRoutingTableReceived = { senderName, senderNodeId, connectedNodes, connectedNodeIds, topologySequence ->
            val applied = meshRouter.updateTopology(senderName, senderNodeId, connectedNodes, connectedNodeIds, myNodeName, topologySequence)
            if (applied) {
                meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
                scheduleOutboxFlush()
                AppLogger.event(
                    category = com.example.testresqmesh.core.utils.TerminalLogCategory.ROUTING,
                    event = "TOPOLOGY_UPDATED",
                    message = "Applied topology version $topologySequence from peer; ${connectedNodes.size} advertised neighbor(s)",
                    peerName = senderName
                )
            }
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
            meshRouter.findShortestPath(myNodeName, targetName, readyConnectedDevices()).isNotEmpty()
        }
        networkManager.canRetireForBridge = { endpoint ->
            meshRouter.canRetireForBridge(myNodeName, endpoint, readyConnectedDevices().filter {
                it.isPeerResponsive && !networkManager.isDeviceBlocked(it.name) && networkManager.hasReadyEndpoint(it.endpointId)
            })
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

        networkManager.onSosCancelled = { /* Unscoped legacy cancellation is ignored. */ }
        networkManager.onConversationMessage = { endpoint, payload ->
            repositoryScope.launch { receiveConversation(endpoint, payload) }
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
                if (!isSystem) recordPeerName(sender)
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
                        isSOS = false,
                        conversationKind = if (isPrivate) "PRIVATE" else if (audio != null) "LEGACY_RADIO" else "COMMUNITY",
                        senderNodeId = NodeIdentity.idOf(sender).orEmpty()
                    )
                    if (isPrivate) {
                        repositoryScope.launch {
                            val inserted = messageStore.saveIncomingPrivateIfAbsent(message)
                            if (inserted == PrivateMessageInsert.REJECTED) return@launch
                            AppLogger.d("MeshNetwork_E2EE", "PRIVATE_STORED message=$msgId insertion=$inserted")
                            val reversedRoute = routePath.reversed().toMutableList()
                            if (reversedRoute.isEmpty()) reversedRoute.add(sender)
                            if (!NodeIdentity.matches(reversedRoute.first(), myNodeName)) reversedRoute.add(0, myNodeName)
                            networkManager.broadcastDeliveredReceipt(msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute)
                            if (inserted == PrivateMessageInsert.DUPLICATE && messageStore.incomingPrivateWasSeen(msgId)) {
                                networkManager.broadcastSeenReceipt(msgId, true, endpointId, reversedRoute)
                            }
                            if (inserted == PrivateMessageInsert.INSERTED) networkManager.showPrivateMessageNotification(sender, text)
                        }
                    } else {
                        repositoryScope.launch {
                            if (!messageStore.contains(msgId)) messageStore.save(message, targetName = null)
                            networkManager.markPayloadStored(msgId)
                            networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
                        }
                        
                        if (message.isSOS) {
                            _incomingSosAlert.value = message
                        }
                    }

                    if (message.conversationKind == "RADIO" && message.audioBase64 != null) {
                        incomingVoiceMessage.tryEmit(message)
                    }
                } else if (!isSystem && !isPrivate) {
                    // Legacy off-channel content is intentionally ignored locally. Onward custody
                    // still has to commit before the journal can release it.
                    networkManager.markPayloadStored(msgId)
                }
            } else if (!isSystem && !isPrivate) {
                // Our own broadcast row already exists when a duplicate returns by another path.
                networkManager.markPayloadStored(msgId)
            }
        }
        networkManager.onMessageDelivered = { msgId, readerName, returnRoute ->
            val currentSos = _incomingSosAlert.value
            if (currentSos?.id == msgId && !currentSos.deliveredTo.contains(readerName)) {
                _incomingSosAlert.value = currentSos.copy(deliveredTo = currentSos.deliveredTo + readerName)
            }

            repositoryScope.launch {
                messageStore.markDelivered(msgId, readerName)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_DELIVERED message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
            }
        }
        
        networkManager.onMessageSeen = { msgId, readerName ->
            repositoryScope.launch {
                messageStore.markSeen(msgId, readerName)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_SEEN message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
            }
        }
    }

    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null) {
        repositoryScope.launch {
            messageStore.markSeen(messageId, "Me")
        }
        
        if (isPrivate && targetName != null) {
            val readyDevices = readyConnectedDevices()
            val route = meshRouter.findShortestPath(myNodeName, targetName, readyDevices)
            val delivery = PrivateDeliveryPlanner.select(targetName, route, readyDevices)
            if (delivery is PrivateDeliveryPlanner.Target.Endpoint) {
                networkManager.broadcastSeenReceipt(messageId, true, delivery.endpointId, route)
            } else {
                AppLogger.d("MeshNetwork_E2EE", "Private seen receipt route unavailable; not broadcasting")
            }
        } else {
            networkManager.broadcastSeenReceipt(messageId, false, null)
        }
    }

    fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        myNodeName = NodeIdentity.qualifiedName(customName, nodeTag, nodeId)
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
        readyPeerEvents.update(emptyList())
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


    private fun awaitPrivateReceipt(messageId: String) {
        privateAcceptedAttempts.merge(messageId, 1, Int::plus)
        val owner = Any()
        privateTimeoutOwners[messageId] = owner
        repositoryScope.launch {
            val acceptedAt = System.nanoTime() / 1_000_000
            if (networkManager.reportsOutboundProgress) {
                while (privateTimeoutOwners[messageId] === owner) {
                    delay(1_000L)
                    if (!messageStore.isUnacknowledgedPrivateSend(messageId)) break
                    val progress = privateProgress[messageId]
                    val now = System.nanoTime() / 1_000_000
                    if (progress?.shouldRetry(now, acceptedAt, networkManager.hasPendingTransfer(messageId)) == true) break
                    if (progress == null && !networkManager.hasPendingTransfer(messageId) && now - acceptedAt >= 60_000L) break
                }
            } else delay(PRIVATE_DELIVERY_TIMEOUT_MS)
            if (!privateTimeoutOwners.remove(messageId, owner)) return@launch
            privateProgress.remove(messageId)
            if (!messageStore.isUnacknowledgedPrivateSend(messageId)) {
                privateAcceptedAttempts.remove(messageId)
                return@launch
            }
            if ((privateAcceptedAttempts[messageId] ?: 0) >= 3) {
                messageStore.markFailed(messageId)
                privateAcceptedAttempts.remove(messageId)
            } else {
                messageStore.markPending(messageId)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_RETRY message=$messageId awaiting recipient receipt")
                scheduleOutboxFlush()
            }
        }
    }

    private fun scheduleOutboxFlush() {
        // Conflate wakes without postponing or cancelling the serialized worker.
        outboxWake.trySend(Unit)
    }

    private fun scheduleOutboxRetry() {
        if (outboxRetryJob?.isActive == true) return
        outboxRetryJob = repositoryScope.launch {
            delay(OUTBOX_RETRY_BACKOFF_MS)
            scheduleOutboxFlush()
        }
    }

    private suspend fun flushOutbox() = outboxMutex.withLock {
        val now = System.currentTimeMillis()
        val pendingMessages = messageStore.getPendingOutbox()
        if (pendingMessages.isEmpty()) return

        val readyDevices = readyConnectedDevices()
        if (readyDevices.isEmpty()) return

        var retryRejectedDispatch = false
        for ((message, targetName) in pendingMessages) {
            if (targetName == null && (message.conversationKind.startsWith("LEGACY_") || message.isSOS ||
                    (message.conversationKind == "SOS" && sosRepository?.canReply(message.sosId) != true))) {
                messageStore.expirePending(message.id)
                continue
            }
            if (now - message.timestamp > OUTBOX_EXPIRY_MS) {
                messageStore.expirePending(message.id)
                continue
            }
            if (targetName != null) {
                // The transport journal already owns this note; do not create a fresh competing
                // transmission simply because a peer disappeared during its chunk transfer.
                if (networkManager.hasPendingTransfer(message.id)) continue
                // Private message retry
                val currentTarget = currentPeerName(targetName)
                // A saved pending row must not bypass the same key-change refusal as a new send.
                if (publicKeys.hasPendingChange(currentTarget)) continue
                val targetPubKey = publicKeys.trustedKey(currentTarget) ?: continue
                val directedRouteList = meshRouter.findShortestPath(myNodeName, currentTarget, readyDevices)
                val payloadBytes = runCatching {
                    PayloadFactory.buildPrivatePayload(
                        msgId = message.id,
                        timestamp = message.timestamp,
                        senderName = myNodeName,
                        targetName = currentTarget,
                        text = message.text,
                        imageBase64 = message.imageBase64,
                        audioBase64 = message.audioBase64,
                        locationLat = message.locationLat,
                        locationLng = message.locationLng,
                        directedRoute = directedRouteList,
                        targetPubKey = targetPubKey,
                        channelId = _currentChannelId.value,
                        transmissionId = UUID.randomUUID().toString()
                    )
                }.getOrNull() ?: continue

                val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
                val transmission = runCatching { ProtoBuf.decodeFromByteArray<com.example.testresqmesh.core.network.MeshPayload>(payloadBytes).id }.getOrNull()
                if (transmission != null) privateProgress[message.id] = PrivateTransferProgress(transmission, directedRouteList.size - 1)
                val dispatchResult = when (delivery) {
                    is PrivateDeliveryPlanner.Target.Endpoint -> {
                        networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
                    }
                    PrivateDeliveryPlanner.Target.Unavailable -> {
                        null
                    }
                }
                if (dispatchResult?.accepted == true) {
                    messageStore.markSent(message.id)
                    awaitPrivateReceipt(message.id)
                } else {
                    privateProgress.remove(message.id)
                    if (dispatchResult != null) retryRejectedDispatch = true
                }
            } else {
                // Public message retry
                val payloadBytes = PayloadFactory.buildPublicPayload(
                    msgId = message.id,
                    timestamp = message.timestamp,
                    senderName = myNodeName,
                    text = message.text,
                    imageBase64 = message.imageBase64,
                    audioBase64 = message.audioBase64,
                    locationLat = message.locationLat,
                    locationLng = message.locationLng,
                    isSOS = message.isSOS,
                    isSOSCancel = false,
                    channelId = message.channelId,
                    conversationKind = message.conversationKind,
                    sosId = message.sosId,
                    senderNodeId = message.senderNodeId.ifBlank { networkManager.myNodeId },
                    ttl = networkManager.currentMeshTtl()
                )
                val result = if (message.isSOS) {
                    networkManager.broadcastPriorityPayload(payloadBytes)
                } else {
                    networkManager.broadcastPayload(payloadBytes)
                }
                if (result.anyAccepted) {
                    messageStore.markSent(message.id)
                } else if (result.neighbors.values.any { it == com.example.testresqmesh.core.network.TransportDispatchResult.REJECTED_INVALID_FRAME }) {
                    messageStore.expirePending(message.id)
                } else {
                    retryRejectedDispatch = true
                }
            }
        }
        if (retryRejectedDispatch) {
            scheduleOutboxRetry()
        }
    }

    private companion object {
        const val BLOCK_REQUEST_TYPE = "BLOCK_REQUEST"
        const val BLOCK_ACK_TYPE = "BLOCK_ACK"
        const val BLOCK_DIRECT_GRACE_MS = 2_000L
        const val BLOCK_ACK_GRACE_MS = 1_000L
        const val BLOCK_RETRY_MS = 3_000L
        const val PRIVATE_DELIVERY_TIMEOUT_MS = 15_000L
        const val OUTBOX_RETRY_BACKOFF_MS = 5_000L
        const val OUTBOX_EXPIRY_MS = 24 * 60 * 60 * 1000L
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

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false,
        conversationKind: String = "COMMUNITY", channelId: String = "", sosId: String = ""): String {
        require(!isSOS && !isSOSCancel) { "SOS lifecycle uses signed SosRepository operations" }
        require(conversationKind in setOf("COMMUNITY", "RADIO", "SOS"))
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
            channelId = channelId,
            conversationKind = conversationKind,
            sosId = sosId,
            senderNodeId = networkManager.myNodeId,
            ttl = networkManager.currentMeshTtl()
        )

        val message = ChatMessage(messageId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp,
            conversationKind = conversationKind, channelId = channelId, sosId = sosId, senderNodeId = networkManager.myNodeId)
        repositoryScope.launch {
            outboxMutex.withLock {
                if (conversationKind == "SOS" && sosRepository?.canReply(sosId) != true) {
                    _publicSendFeedback.emit("This SOS has ended. Its conversation is read-only.")
                    return@withLock
                }
                // Persist first, so acceptance and receipts cannot race an absent database row.
                messageStore.save(message.copy(deliveredTo = listOf("PENDING")), targetName = null)
                val result = if (readyConnectedDevices().isEmpty()) {
                    com.example.testresqmesh.core.network.BroadcastDispatchResult(emptyMap())
                } else if (isSOS) networkManager.broadcastPriorityPayload(payloadBytes) else networkManager.broadcastPayload(payloadBytes)
                if (result.anyAccepted) {
                    messageStore.markSent(messageId)
                } else if (result.neighbors.values.any { it == com.example.testresqmesh.core.network.TransportDispatchResult.REJECTED_INVALID_FRAME }) {
                    messageStore.expirePending(messageId)
                } else {
                    scheduleOutboxRetry()
                }
                result.feedback()?.let { _publicSendFeedback.emit(it) }
            }
        }
        return messageId
    }

    private suspend fun receiveConversation(endpoint: String, p: com.example.testresqmesh.core.network.MeshPayload) {
        if (p.senderNodeId.isBlank() || p.senderNodeId == networkManager.myNodeId || p.id.length !in 1..100 ||
            p.senderName.length !in 1..160 || p.text.length > 8192 || p.isPrivate || p.isEncrypted ||
            p.conversationKind !in setOf("COMMUNITY", "RADIO", "SOS") || p.ttl !in 1..10 || p.relayHopCount !in 0..10) return
        if (p.conversationKind == "RADIO" && p.channelId !in (1..5).map { it.toString() }) return
        if (p.conversationKind == "SOS" && (p.audioBytes != null || p.imageBytes != null || sosRepository?.canReply(p.sosId) != true)) return
        if ((p.audioBytes?.size ?: 0) > 2 * 1024 * 1024 || (p.imageBytes?.size ?: 0) > 2 * 1024 * 1024) return
        val attachments = runCatching {
            val audio = p.audioBytes?.let { Base64.encodeToString(com.example.testresqmesh.core.utils.BinaryCompressor.decompress(it, 2 * 1024 * 1024), Base64.NO_WRAP) }
            val image = p.imageBytes?.let { Base64.encodeToString(com.example.testresqmesh.core.utils.BinaryCompressor.decompress(it, 2 * 1024 * 1024), Base64.NO_WRAP) }
            audio to image
        }.getOrElse {
            AppLogger.d("CONVERSATION", "Rejected invalid attachment")
            return
        }
        var fresh = false
        var message: ChatMessage? = null
        outboxMutex.withLock {
            if (!messageStore.contains(p.id)) {
                message = ChatMessage(p.id, p.senderName, p.text, attachments.second, attachments.first, p.locationLat, p.locationLng,
                    false, timestamp = System.currentTimeMillis(), isHopped = p.relayHopCount > 0,
                    outboundRoute = p.routePath, conversationKind = p.conversationKind, channelId = p.channelId,
                    sosId = p.sosId, senderNodeId = p.senderNodeId)
                messageStore.save(message!!, null)
                fresh = true
            }
        }
        networkManager.broadcastDeliveredReceipt(p.id, isPrivate = false)
        if (!fresh && !networkManager.hasPendingCustody(p.id)) return
        if (fresh) recordPeerName(p.senderName)
        if (fresh) message?.takeIf { it.conversationKind == "RADIO" && it.audioBase64 != null }?.let { incomingVoiceMessage.tryEmit(it) }
        if (p.ttl > 1 && p.relayHopCount < 10) {
            networkManager.broadcastPayload(ProtoBuf.encodeToByteArray(p.copy(ttl = p.ttl - 1,
                relayHopCount = p.relayHopCount + 1, routePath = p.routePath + myNodeName)), endpoint)
        }
        networkManager.markPayloadStored(p.id)
    }

    fun deleteConversationWith(peerName: String) {
        repositoryScope.launch {
            messageStore.deleteConversation(peerName)
        }
    }

    fun hasPendingPublicKeyChange(peerName: String): Boolean = publicKeys.hasPendingChange(peerName)

    fun acceptPendingPublicKeyChange(peerName: String): Boolean = publicKeys.acceptPendingChange(peerName)

    fun rejectPendingPublicKeyChange(peerName: String): Boolean = publicKeys.rejectPendingChange(peerName)

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null): Boolean {
        val msgId = UUID.randomUUID().toString()
        AppLogger.d("MeshNetwork_E2EE", "PRIVATE_SEND_REQUEST message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
        val timestamp = System.currentTimeMillis()
        val currentTarget = currentPeerName(targetName)
        if (publicKeys.hasPendingChange(currentTarget)) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: recipient key change requires approval")
            return false
        }
        
        val readyDevices = readyConnectedDevices()
        val directedRouteList = if (readyDevices.isNotEmpty()) meshRouter.findShortestPath(myNodeName, currentTarget, readyDevices) else emptyList()
        val targetPubKey = publicKeys.trustedKey(currentTarget)
        
        // If we are completely offline or route is unavailable, save as Pending outbox message
        if (readyDevices.isEmpty() || targetPubKey == null || directedRouteList.isEmpty()) {
            val message = ChatMessage(
                id = msgId,
                senderName = myNodeName,
                text = text,
                imageBase64 = imageBase64,
                audioBase64 = audioBase64,
                locationLat = locationLat,
                locationLng = locationLng,
                isMine = true,
                isPrivate = true,
                timestamp = timestamp,
                isHopped = false,
                outboundRoute = emptyList()
            )
            repositoryScope.launch {
                messageStore.save(message, targetName = currentTarget)
                messageStore.markPending(msgId)
                scheduleOutboxFlush()
            }
            AppLogger.d("MeshNetwork_E2EE", "Private send queued as Pending for $targetName (no immediate route)")
            return true
        }

        val payloadBytes = runCatching { PayloadFactory.buildPrivatePayload(
            msgId = msgId,
            timestamp = timestamp,
            senderName = myNodeName,
            targetName = currentTarget,
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

        val delivery = PrivateDeliveryPlanner.select(currentTarget, directedRouteList, readyDevices)
        val isDirect = readyDevices.any { NodeIdentity.matches(it.name, currentTarget) }
        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        
        repositoryScope.launch {
            outboxMutex.withLock {
                // Persist before dispatch so a fast receipt cannot race the local message row.
                messageStore.save(message, targetName = currentTarget)
                messageStore.markPending(msgId)
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_PERSISTED message=$msgId elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
                privateProgress[msgId] = PrivateTransferProgress(msgId, directedRouteList.size - 1)
                val dispatchResult = when (delivery) {
                    is PrivateDeliveryPlanner.Target.Endpoint -> {
                        AppLogger.d("MeshNetwork_E2EE", "Private route selected with ${directedRouteList.size - 1} hop(s)")
                        networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
                    }
                    PrivateDeliveryPlanner.Target.Unavailable -> null
                }
                AppLogger.d("MeshNetwork_E2EE", "PRIVATE_DISPATCH message=$msgId result=$dispatchResult elapsedMs=${android.os.SystemClock.elapsedRealtime()}")
                if (dispatchResult?.accepted == true) {
                    messageStore.markSent(msgId)
                    awaitPrivateReceipt(msgId)
                } else {
                    privateProgress.remove(msgId)
                    messageStore.markPending(msgId)
                    AppLogger.d("MeshNetwork_E2EE", "Private dispatch was not accepted; retained for directed retry")
                    scheduleOutboxRetry()
                }
            }
        }
        return true
    }
}
