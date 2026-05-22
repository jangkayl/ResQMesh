package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.network.MeshNetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

class MeshRepository(private val networkManager: MeshNetworkManager) {

    private val _connectionStatus = MutableStateFlow("Ready to deploy Mesh Node.")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _publicMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val publicMessages = _publicMessages.asStateFlow()

    // Key: targetEndpointId for private messages
    private val _privateMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val privateMessages = _privateMessages.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        networkManager.onStatusChanged = { status ->
            _connectionStatus.value = status
        }

        networkManager.onDeviceConnected = { device ->
            if (_connectedDevices.value.none { it.endpointId == device.endpointId }) {
                _connectedDevices.value = _connectedDevices.value + device
                _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != device.endpointId }
            }
        }

        networkManager.onDeviceDisconnected = { endpointId ->
            _connectedDevices.value = _connectedDevices.value.filter { it.endpointId != endpointId }
        }

        networkManager.onDeviceScanned = { id, name, score, role, isConnecting ->
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

        networkManager.onDeviceScanRemoved = { id ->
            _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != id }
        }

        networkManager.onMessageReceived = { endpointId, sender, text, isPrivate, isSystem, img, audio, lat, lng ->
            if (!isSystem) {
                val message = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderName = sender,
                    text = text,
                    imageBase64 = img,
                    audioBase64 = audio,
                    locationLat = lat,
                    locationLng = lng,
                    isMine = false,
                    isPrivate = isPrivate,
                    timestamp = System.currentTimeMillis()
                )
                if (isPrivate) {
                    val currentMap = _privateMessages.value.toMutableMap()
                    val log = currentMap[endpointId] ?: emptyList()
                    currentMap[endpointId] = log + message
                    _privateMessages.value = currentMap
                } else {
                    _publicMessages.value = _publicMessages.value + message
                }
            }
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
        _publicMessages.value = emptyList()
        _privateMessages.value = emptyMap()
    }

    fun rescan() {
        networkManager.rescan()
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", myNodeName)
            put("text", text)
            put("isPrivate", false)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, locationLat, locationLng, true, false, timestamp)
        _publicMessages.value = _publicMessages.value + message
        networkManager.broadcastPayload(jsonString)
    }

    fun sendPrivateMessage(target: ConnectedDevice, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null) {
        val msgId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("timestamp", timestamp)
            put("senderName", myNodeName)
            put("text", text)
            put("isPrivate", true)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
            if (locationLat != null) put("locationLat", locationLat)
            if (locationLng != null) put("locationLng", locationLng)
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, locationLat, locationLng, true, true, timestamp)
        val currentMap = _privateMessages.value.toMutableMap()
        val log = currentMap[target.endpointId] ?: emptyList()
        currentMap[target.endpointId] = log + message
        _privateMessages.value = currentMap

        networkManager.sendDirectPayload(target.endpointId, jsonString)
    }
}
