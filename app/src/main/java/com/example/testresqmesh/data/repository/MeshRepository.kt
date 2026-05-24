package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.network.MeshNetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID

class MeshRepository(private val networkManager: MeshNetworkManager) {

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

    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    val knownNodes = _knownNodes.asStateFlow()

    private val _publicMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val publicMessages = _publicMessages.asStateFlow()

    // Key: targetEndpointId for private messages
    private val _privateMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val privateMessages = _privateMessages.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""

    private val networkGraph = mutableMapOf<String, Set<String>>()
    private val lastSeenMap = mutableMapOf<String, Long>()
    private val repositoryScope = CoroutineScope(Dispatchers.Default)

    init {
        setupCallbacks()
        startTopologyCleanup()
    }

    private fun setupCallbacks() {
        networkManager.onStatusChanged = { status ->
            _connectionStatus.value = status
        }

        networkManager.onDeviceConnected = { device ->
            if (_connectedDevices.value.none { it.endpointId == device.endpointId }) {
                _connectedDevices.value = _connectedDevices.value + device
                _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != device.endpointId }
                
                lastSeenMap[device.name] = System.currentTimeMillis()
                recalculateKnownNodes()
            }
        }

        networkManager.onDeviceDisconnected = { endpointId ->
            _connectedDevices.value = _connectedDevices.value.filter { it.endpointId != endpointId }
            recalculateKnownNodes()
        }
        
        networkManager.onRoutingTableReceived = { senderName, connectedNodes ->
            if (senderName != myNodeName) {
                lastSeenMap[senderName] = System.currentTimeMillis()
                networkGraph[senderName] = connectedNodes.toSet()
                
                // Also update lastSeen for all the nodes they are connected to
                connectedNodes.forEach { node ->
                    if (node != myNodeName) {
                        lastSeenMap[node] = System.currentTimeMillis()
                    }
                }
                recalculateKnownNodes()
            }
        }

        networkManager.onDeviceScanned = { id, name, score, role, isConnecting ->
            // Prevent local node from appearing in the scan list
            if (name != myNodeName) {
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
                            isConnecting = isConnecting || currentScanned[existingIndex].isConnecting
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
                lastSeenMap[sender] = System.currentTimeMillis()
                recalculateKnownNodes()

                if (!isSystem) {
                    val isDirect = _connectedDevices.value.any { it.name == sender }
                    val message = ChatMessage(
                        id = msgId, // Use exact sender's ID!
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
                        val currentMap = _privateMessages.value.toMutableMap()
                        val log = currentMap[sender] ?: emptyList()
                        currentMap[sender] = log + message
                        _privateMessages.value = currentMap
                        
                        // The moment we save a private message, tell the sender we received it using reversed strict routing!
                        val reversedRoute = routePath.reversed().toMutableList()
                        if (reversedRoute.isNotEmpty() && reversedRoute.first() != myNodeName) {
                            reversedRoute.add(0, myNodeName)
                        }
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = true, targetId = endpointId, directedReturnRoute = reversedRoute)
                    } else {
                        _publicMessages.value = _publicMessages.value + message
                        
                        // The moment we save a public message, broadcast that we received it!
                        networkManager.broadcastDeliveredReceipt(msgId, isPrivate = false)
                        
                        if (message.isSOS) {
                            _incomingSosAlert.value = message
                        }
                    }
                }
            }
        }
        
        networkManager.onMessageDelivered = { msgId, readerName, returnRoute ->
            // Update Incoming SOS if it matches
            val currentSos = _incomingSosAlert.value
            if (currentSos?.id == msgId && !currentSos.deliveredTo.contains(readerName)) {
                _incomingSosAlert.value = currentSos.copy(deliveredTo = currentSos.deliveredTo + readerName)
            }

            // Update Public Messages
            val updatedPublic = _publicMessages.value.map { msg ->
                if (msg.id == msgId && !msg.deliveredTo.contains(readerName)) {
                    msg.copy(deliveredTo = msg.deliveredTo + readerName, returnRoute = returnRoute)
                } else {
                    msg
                }
            }
            _publicMessages.value = updatedPublic

            // Update Private Messages
            val updatedPrivate = _privateMessages.value.mapValues { entry ->
                entry.value.map { msg ->
                    if (msg.id == msgId && !msg.deliveredTo.contains(readerName)) {
                        msg.copy(deliveredTo = msg.deliveredTo + readerName, returnRoute = returnRoute)
                    } else {
                        msg
                    }
                }
            }
            _privateMessages.value = updatedPrivate
        }
        
        networkManager.onMessageSeen = { msgId, readerName ->
            // Update Public Messages
            val updatedPublic = _publicMessages.value.map { msg ->
                if (msg.id == msgId && !msg.seenBy.contains(readerName)) {
                    msg.copy(seenBy = msg.seenBy + readerName)
                } else {
                    msg
                }
            }
            _publicMessages.value = updatedPublic

            // Update Private Messages
            val updatedPrivate = _privateMessages.value.mapValues { entry ->
                entry.value.map { msg ->
                    if (msg.id == msgId && !msg.seenBy.contains(readerName)) {
                        msg.copy(seenBy = msg.seenBy + readerName)
                    } else {
                        msg
                    }
                }
            }
            _privateMessages.value = updatedPrivate
        }
    }

    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetId: String? = null) {
        networkManager.broadcastSeenReceipt(messageId, isPrivate, targetId)
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
        _knownNodes.value = emptyList()
        _publicMessages.value = emptyList()
        _privateMessages.value = emptyMap()
    }

    fun disconnectDevice(endpointId: String) {
        networkManager.disconnectFromEndpoint(endpointId)
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
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", myNodeName)
            put("text", text)
            put("isPrivate", false)
            put("isSystem", false)
            put("isSOS", isSOS)
            put("isSOSCancel", isSOSCancel)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp, isSOS = isSOS)
        _publicMessages.value = _publicMessages.value + message
        networkManager.broadcastPayload(jsonString)
        return msgId
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", myNodeName)
            put("targetName", targetName) // ADVANCED ROUTING: Specify target explicitly!
            put("text", text)
            put("isPrivate", true)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
            put("routePath", org.json.JSONArray().apply { put(myNodeName) })
            
            val directedRoute = findShortestPath(targetName)
            if (directedRoute.isNotEmpty()) {
                put("directedRoute", org.json.JSONArray(directedRoute))
            }
        }.toString()

        val isDirect = _connectedDevices.value.any { it.name == targetName }
        val directedRouteList = findShortestPath(targetName)
        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp, isHopped = !isDirect, outboundRoute = directedRouteList)
        val currentMap = _privateMessages.value.toMutableMap()
        val log = currentMap[targetName] ?: emptyList()
        currentMap[targetName] = log + message
        _privateMessages.value = currentMap

        val directEndpointId = _connectedDevices.value.find { it.name == targetName }?.endpointId

        if (isDirect && directEndpointId != null) {
            networkManager.sendDirectPayload(directEndpointId, jsonString)
        } else {
            // STRICT DIRECTED SOURCE ROUTING
            if (directedRouteList.size > 1) {
                val nextHopName = directedRouteList[1]
                val nextHopEndpointId = _connectedDevices.value.find { it.name == nextHopName }?.endpointId
                if (nextHopEndpointId != null) {
                    networkManager.sendDirectPayload(nextHopEndpointId, jsonString)
                } else {
                    // Fallback to flood if topology is stale
                    networkManager.broadcastPayload(jsonString)
                }
            } else {
                networkManager.broadcastPayload(jsonString)
            }
        }
    }

    private fun findShortestPath(targetName: String): List<String> {
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()
        
        queue.add(listOf(myNodeName))
        visited.add(myNodeName)
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentNode = path.last()
            
            if (currentNode == targetName) {
                return path
            }
            
            val neighbors = mutableSetOf<String>()
            if (currentNode == myNodeName) {
                neighbors.addAll(_connectedDevices.value.map { it.name })
            } else {
                networkGraph[currentNode]?.let { neighbors.addAll(it) }
            }
            
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(path + neighbor)
                }
            }
        }
        return emptyList()
    }

    private fun recalculateKnownNodes() {
        val newKnownNodes = mutableListOf<KnownNode>()
        // Add direct nodes
        _connectedDevices.value.forEach { device ->
            val lastSeen = lastSeenMap[device.name] ?: System.currentTimeMillis()
            newKnownNodes.add(KnownNode(device.name, isDirect = true, lastSeen = lastSeen))
        }
        
        // Add indirect nodes from the graph
        networkGraph.values.flatten().toSet().forEach { indirectNode ->
            // Don't add ourselves, and don't add if already in direct nodes
            if (indirectNode != myNodeName && newKnownNodes.none { it.name == indirectNode }) {
                val lastSeen = lastSeenMap[indirectNode] ?: System.currentTimeMillis()
                newKnownNodes.add(KnownNode(indirectNode, isDirect = false, lastSeen = lastSeen))
            }
        }
        
        _knownNodes.value = newKnownNodes
    }

    private fun startTopologyCleanup() {
        repositoryScope.launch {
            while (true) {
                delay(5000)
                val now = System.currentTimeMillis()
                var changed = false
                
                // Purge nodes older than 20 seconds (4 missed pulses)
                val iterator = lastSeenMap.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > 10000) {
                        val deadNode = entry.key
                        iterator.remove()
                        networkGraph.remove(deadNode)
                        changed = true
                    }
                }
                
                if (changed) {
                    recalculateKnownNodes()
                }
            }
        }
    }
}
