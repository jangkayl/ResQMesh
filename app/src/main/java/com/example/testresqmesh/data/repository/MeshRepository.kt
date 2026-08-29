package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.network.NativeBleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.testresqmesh.data.local.entity.toMessageEntity
import java.util.UUID

class MeshRepository(
    private val networkManager: NativeBleManager,
    private val appDatabase: com.example.testresqmesh.data.local.AppDatabase
) {

    private val _connectionStatus = MutableStateFlow("Ready to deploy Mesh Node.")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _incomingSosAlert = MutableStateFlow<ChatMessage?>(null)
    val incomingSosAlert: StateFlow<ChatMessage?> = _incomingSosAlert.asStateFlow()

    fun clearSosAlert() {
        _incomingSosAlert.value = null
    }

    private val meshRouter = MeshRouter()
    val knownNodes = meshRouter.knownNodes

    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    val publicMessages = appDatabase.messageDao().getPublicMessages().map { list ->
        list.map { it.toChatMessage() }
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val privateMessages = appDatabase.messageDao().getAllPrivateMessages().map { list ->
        list.groupBy {
            if (it.isMine) it.targetName ?: it.senderName else it.senderName
        }.mapValues { entry ->
            entry.value.map { it.toChatMessage() }
        }
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""
    private val publicKeys = mutableMapOf<String, String>()

    init {
        setupCallbacks()
        meshRouter.startTopologyCleanup(repositoryScope, { myNodeName }, { _connectedDevices.value })
    }

    private fun setupCallbacks() {
        networkManager.onStatusChanged = { status ->
            _connectionStatus.value = status
        }

        networkManager.onDeviceConnected = { device ->
            val existing = _connectedDevices.value.find { it.endpointId == device.endpointId }
            if (existing != null) {
                if (device.isClassicConnected != existing.isClassicConnected) {
                    _connectedDevices.value = _connectedDevices.value.map {
                        if (it.endpointId == device.endpointId) device else it
                    }
                }
            } else {
                _connectedDevices.value = _connectedDevices.value + device
                _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != device.endpointId }
                
                meshRouter.markNodeSeen(device.name)
                meshRouter.recalculateKnownNodes(myNodeName, _connectedDevices.value)
            }
        }

        networkManager.onDeviceDisconnected = { endpointId ->
            val disconnectedDevice = _connectedDevices.value.find { it.endpointId == endpointId }
            _connectedDevices.value = _connectedDevices.value.filter { it.endpointId != endpointId }
            if (disconnectedDevice != null) {
                meshRouter.removeNode(disconnectedDevice.name)
            }
            meshRouter.recalculateKnownNodes(myNodeName, _connectedDevices.value)
        }
        
        networkManager.onPublicKeyReceived = { senderName, key ->
            publicKeys[senderName] = key
        }
        
        networkManager.onRoutingTableReceived = { senderName, connectedNodes ->
            meshRouter.updateTopology(senderName, connectedNodes, myNodeName)
            meshRouter.recalculateKnownNodes(myNodeName, _connectedDevices.value)
        }



        networkManager.onDeviceScanned = { id, name, score, role, isConnecting ->
            if (name != myNodeName && name != myNodeName.take(20)) {
                val isNotConnected = _connectedDevices.value.none { it.endpointId == id || it.name == name }
                
                if (isNotConnected) {
                    val currentScanned = _scannedDevices.value.toMutableList()
                    val existingIndex = currentScanned.indexOfFirst { it.name == name }
                    
                    if (existingIndex != -1) {
                        currentScanned[existingIndex] = currentScanned[existingIndex].copy(
                            endpointId = id,
                            lastSeen = System.currentTimeMillis(),
                            powerScore = score,
                            myRole = role,
                            isConnecting = isConnecting
                        )
                    } else {
                        currentScanned.add(ScannedDevice(id, name, System.currentTimeMillis(), score, role, isConnecting))
                    }
                    _scannedDevices.value = currentScanned
                }
            }
        }

        networkManager.onDeviceScanRemoved = { id ->
            _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != id }
        }

        networkManager.onSosCancelled = {
            clearSosAlert()
        }

        networkManager.onMessageReceived = { endpointId, msgId, sender, text, isPrivate, isSystem, img, audio, lat, lng, medium, routePath ->
            if (sender != myNodeName) {
                meshRouter.markNodeSeen(sender)
                meshRouter.recalculateKnownNodes(myNodeName, _connectedDevices.value)

                if (!isSystem) {
                    val isDirect = _connectedDevices.value.any { it.name == sender }
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
                            appDatabase.messageDao().insertMessage(message.toMessageEntity(targetName = sender))
                        }
                        
                        val reversedRoute = routePath.reversed().toMutableList()
                        if (reversedRoute.isNotEmpty() && reversedRoute.first() != myNodeName) {
                            reversedRoute.add(0, myNodeName)
                        }
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute)
                    } else {
                        repositoryScope.launch {
                            appDatabase.messageDao().insertMessage(message.toMessageEntity(targetName = null))
                        }
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
                        
                        if (message.isSOS) {
                            _incomingSosAlert.value = message
                        }
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
                val msg = appDatabase.messageDao().getMessageById(msgId)
                if (msg != null && !msg.deliveredTo.split(",").contains(readerName)) {
                    val newDelivered = if (msg.deliveredTo.isEmpty()) readerName else "${msg.deliveredTo},$readerName"
                    appDatabase.messageDao().updateDeliveredTo(msgId, newDelivered)
                }
            }
        }
        
        networkManager.onMessageSeen = { msgId, readerName ->
            repositoryScope.launch {
                val msg = appDatabase.messageDao().getMessageById(msgId)
                if (msg != null && !msg.seenBy.split(",").contains(readerName)) {
                    val newSeen = if (msg.seenBy.isEmpty()) readerName else "${msg.seenBy},$readerName"
                    appDatabase.messageDao().updateSeenBy(msgId, newSeen)
                }
            }
        }
    }

    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null) {
        repositoryScope.launch {
            val msg = appDatabase.messageDao().getMessageById(messageId)
            if (msg != null && !msg.seenBy.split(",").contains("Me")) {
                val newSeen = if (msg.seenBy.isEmpty()) "Me" else "${msg.seenBy},Me"
                appDatabase.messageDao().updateSeenBy(messageId, newSeen)
            }
        }
        
        val directEndpointId = _connectedDevices.value.find { it.name == targetName }?.endpointId
        if (directEndpointId != null) {
            networkManager.broadcastSeenReceipt(messageId, isPrivate, directEndpointId)
        } else {
            networkManager.broadcastSeenReceipt(messageId, isPrivate, null)
        }
    }

    fun startNode(customName: String, nodeTag: String, teamKey: String) {
        myNodeName = "$customName [$nodeTag]"
        networkManager.myDeviceName = myNodeName
        networkManager.startMeshNode(teamKey)
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

    fun blockDevice(deviceName: String) {
        _blockedDeviceNames.value = _blockedDeviceNames.value + deviceName
    }

    fun unblockDevice(deviceName: String) {
        _blockedDeviceNames.value = _blockedDeviceNames.value - deviceName
    }

    fun forceConnect(endpointId: String, endpointName: String) {
        networkManager.forceConnectToDevice(endpointId, endpointName)
    }

    fun rescan() {
        networkManager.rescan()
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false): String {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val payloadBytes = PayloadFactory.buildPublicPayload(
            msgId = msgId,
            timestamp = timestamp,
            senderName = myNodeName,
            text = text,
            imageBase64 = imageBase64,
            audioBase64 = audioBase64,
            locationLat = locationLat,
            locationLng = locationLng,
            isSOS = isSOS,
            isSOSCancel = isSOSCancel
        )

        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp, isSOS = isSOS)
        repositoryScope.launch {
            appDatabase.messageDao().insertMessage(message.toMessageEntity(targetName = null))
        }
        networkManager.broadcastPayload(payloadBytes)
        return msgId
    }

    fun deleteConversationWith(peerName: String) {
        repositoryScope.launch {
            appDatabase.messageDao().deleteConversationWith(peerName)
        }
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val directedRouteList = meshRouter.findShortestPath(myNodeName, targetName, _connectedDevices.value)
        val targetPubKey = publicKeys[targetName]

        val payloadBytes = PayloadFactory.buildPrivatePayload(
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
            targetPubKey = targetPubKey
        )

        val isDirect = _connectedDevices.value.any { it.name == targetName }
        val message = ChatMessage(msgId, myNodeName, text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        
        repositoryScope.launch {
            appDatabase.messageDao().insertMessage(message.toMessageEntity(targetName = targetName))
        }

        val directEndpointId = _connectedDevices.value.find { it.name == targetName }?.endpointId

        if (isDirect && directEndpointId != null) {
            networkManager.sendDirectPayload(directEndpointId, payloadBytes)
        } else {
            if (directedRouteList.size > 1) {
                val nextHopName = directedRouteList[1]
                val nextHopEndpointId = _connectedDevices.value.find { it.name == nextHopName }?.endpointId
                if (nextHopEndpointId != null) {
                    networkManager.sendDirectPayload(nextHopEndpointId, payloadBytes)
                } else {
                    networkManager.broadcastPayload(payloadBytes)
                }
            } else {
                networkManager.broadcastPayload(payloadBytes)
            }
        }
    }
}
