package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.models.ChatMessage
import com.example.testresqmesh.data.models.ConnectedDevice
import com.example.testresqmesh.data.models.ScannedDevice
import com.example.testresqmesh.network.MeshNetworkManager
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

        networkManager.onDeviceScanned = { id, name ->
            val currentScanned = _scannedDevices.value
            val isNotConnected = _connectedDevices.value.none { it.endpointId == id }
            
            if (isNotConnected) {
                val index = currentScanned.indexOfFirst { it.endpointId == id }
                if (index != -1) {
                    val updated = currentScanned.toMutableList()
                    updated[index] = updated[index].copy(lastSeen = System.currentTimeMillis())
                    _scannedDevices.value = updated
                } else {
                    _scannedDevices.value = currentScanned + ScannedDevice(id, name, System.currentTimeMillis())
                }
            }
        }

        networkManager.onDeviceScanRemoved = { id ->
            _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != id }
        }

        networkManager.onMessageReceived = { endpointId, sender, text, isPrivate, isSystem, img, audio ->
            if (!isSystem) {
                val message = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderName = sender,
                    text = text,
                    imageBase64 = img,
                    audioBase64 = audio,
                    isMine = false,
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

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?) {
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
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, true, timestamp)
        _publicMessages.value = _publicMessages.value + message
        networkManager.broadcastPayload(jsonString)
    }

    fun sendPrivateMessage(target: ConnectedDevice, text: String, imageBase64: String?, audioBase64: String?) {
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
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, true, timestamp)
        val currentMap = _privateMessages.value.toMutableMap()
        val log = currentMap[target.endpointId] ?: emptyList()
        currentMap[target.endpointId] = log + message
        _privateMessages.value = currentMap

        networkManager.sendDirectPayload(target.endpointId, jsonString)
    }
}
