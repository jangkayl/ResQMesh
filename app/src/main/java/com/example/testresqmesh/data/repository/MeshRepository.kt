package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MeshRepository(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
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
    private val publicKeys = PeerPublicKeyCache()

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
                publicKeys.observeDirectLink(device.name, device.endpointId)
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
                publicKeys.forgetDirectLink(disconnectedDevice.name, endpointId)
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
        
        networkManager.onPublicKeyReceived = { endpointId, senderName, key ->
            publicKeys.put(senderName, key, endpointId)
            AppLogger.d("BLE_MESH", "Received recipient public key for $senderName via $endpointId")
        }
        
        networkManager.onRoutingTableReceived = { senderName, connectedNodes ->
            meshRouter.updateTopology(senderName, connectedNodes, myNodeName)
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
            AppLogger.event(
                category = com.example.testresqmesh.core.utils.TerminalLogCategory.ROUTING,
                event = "TOPOLOGY_UPDATED",
                message = "Applied topology from peer; ${connectedNodes.size} advertised neighbor(s)",
                peerName = senderName
            )
        }

        networkManager.onDeviceBlocked = { senderName ->
            _blockedDeviceNames.value = _blockedDeviceNames.value
                .filterNot { NodeIdentity.matches(it, senderName) }
                .toSet() + senderName
            networkManager.blockDevice(senderName)
        }

        networkManager.onDeviceUnblocked = { senderName ->
            _blockedDeviceNames.value = _blockedDeviceNames.value
                .filterNot { NodeIdentity.matches(it, senderName) }
                .toSet()
            networkManager.unblockDevice(senderName)
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
            if (channelId == _currentChannelId.value && !networkManager.isDeviceBlocked(sender)) {
                repositoryScope.launch {
                    incomingLiveAudioChunk.emit(Pair(sender, chunk))
                }
            }
        }

        networkManager.onMessageReceived = { endpointId, msgId, sender, text, isPrivate, isSystem, img, audio, lat, lng, medium, routePath, channelId ->
            if (sender != myNodeName) {
                meshRouter.markNodeSeen(sender)
                meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())

                if (!isSystem && (isPrivate || channelId == _currentChannelId.value) && !networkManager.isDeviceBlocked(sender)) {
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
        publicKeys.clear()
        myNodeName = "$customName [$nodeTag]#$nodeId"
        networkManager.myDeviceName = myNodeName
        networkManager.myNodeId = nodeId
        networkManager.startMeshNode(teamKey)
        _isOnline.value = true
    }

    fun stopNode() {
        publicKeys.clear()
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

    fun blockDevice(deviceName: String) {
        _blockedDeviceNames.value = _blockedDeviceNames.value
            .filterNot { NodeIdentity.matches(it, deviceName) }
            .toSet() + deviceName
        networkManager.blockDevice(deviceName)
        sendSystemCommand(deviceName, "BLOCK")
    }

    fun unblockDevice(deviceName: String) {
        _blockedDeviceNames.value = _blockedDeviceNames.value
            .filterNot { NodeIdentity.matches(it, deviceName) }
            .toSet()
        networkManager.unblockDevice(deviceName)
        sendSystemCommand(deviceName, "UNBLOCK")
    }

    private fun sendSystemCommand(targetName: String, commandType: String) {
        val payloadBytes = runCatching { PayloadFactory.buildPrivatePayload(
            msgId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            senderName = myNodeName,
            targetName = targetName,
            text = "",
            imageBase64 = null,
            audioBase64 = null,
            locationLat = null,
            locationLng = null,
            directedRoute = meshRouter.findShortestPath(myNodeName, targetName, readyConnectedDevices()),
            targetPubKey = publicKeys.get(targetName),
            channelId = _currentChannelId.value
        ) }.getOrElse {
            AppLogger.d("MeshNetwork_E2EE", "Could not send private control command to $targetName: recipient key unavailable or encryption failed")
            return
        }.let {
            // We need to override the type in the byte array or construct a custom payload.
            // Since PayloadFactory builds ChatMessage payloads, we'll decode, change type, encode.
            val decoded = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), it)
            val updated = decoded.copy(type = commandType)
            kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), updated)
        }
        networkManager.broadcastPayload(payloadBytes)
    }

    fun broadcastLiveAudioChunk(chunk: ByteArray) {
        val messageId = java.util.UUID.randomUUID().toString()
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = messageId,
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
        networkManager.broadcastPayload(payloadBytes)
        return messageId
    }

    fun deleteConversationWith(peerName: String) {
        repositoryScope.launch {
            messageStore.deleteConversation(peerName)
        }
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null): Boolean {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val readyDevices = readyConnectedDevices()
        if (readyDevices.isEmpty()) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: no payload-ready peer link")
            return false
        }
        val directedRouteList = meshRouter.findShortestPath(myNodeName, targetName, readyDevices)
        val targetPubKey = publicKeys.get(targetName)
        if (targetPubKey == null) {
            AppLogger.d("MeshNetwork_E2EE", "Private send blocked: no recipient public key for $targetName")
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

        val isDirect = readyDevices.any { NodeIdentity.matches(it.name, targetName) }
        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        
        repositoryScope.launch {
            messageStore.save(message, targetName = targetName)
        }

        when (val delivery = PrivateDeliveryPlanner.select(targetName, directedRouteList, readyDevices)) {
            is PrivateDeliveryPlanner.Target.Endpoint ->
                networkManager.sendDirectPayload(delivery.endpointId, payloadBytes)
            PrivateDeliveryPlanner.Target.Broadcast ->
                networkManager.broadcastPayload(payloadBytes)
        }
        return true
    }
}
