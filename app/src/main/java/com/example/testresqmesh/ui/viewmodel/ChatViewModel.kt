package com.example.testresqmesh.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.models.ChatMessage
import com.example.testresqmesh.data.models.ConnectedDevice
import com.example.testresqmesh.network.MeshNetworkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

// Change it to this:
data class ScannedDevice(val endpointId: String, val name: String, val lastSeen: Long)

class ChatViewModel(private val networkManager: MeshNetworkManager) : ViewModel() {

    // States observed by UI
    var isOnline by mutableStateOf(false)
    var isRescanning by mutableStateOf(false)
    var connectionStatus by mutableStateOf("Ready to deploy Mesh Node.")
    var myNodeName by mutableStateOf("")

    var teamKey by mutableStateOf("PUBLIC")

    val connectedDevices = mutableStateListOf<ConnectedDevice>()
    val publicMessages = mutableStateListOf<ChatMessage>()
    val privateMessages = mutableStateMapOf<String, List<ChatMessage>>()
    val activeMeshNodes = mutableStateMapOf<String, Long>()
    val scannedDevices = mutableStateListOf<ScannedDevice>()

    init {
        setupNetworkCallbacks()
        startMeshGarbageCollector()

        networkManager.onDeviceScanned = { id, name ->
            val notConnected = connectedDevices.none { it.endpointId == id }

            if (notConnected) {
                // If they are already in the radar, just update their timestamp
                val index = scannedDevices.indexOfFirst { it.endpointId == id }
                if (index != -1) {
                    scannedDevices[index] = scannedDevices[index].copy(lastSeen = System.currentTimeMillis())
                } else {
                    // It's a new device, add them!
                    scannedDevices.add(ScannedDevice(id, name, System.currentTimeMillis()))
                }
            }
        }

        networkManager.onDeviceScanRemoved = { id ->
            scannedDevices.removeAll { it.endpointId == id }
        }
    }

    private fun setupNetworkCallbacks() {
        networkManager.onStatusChanged = { status ->
            connectionStatus = status
            if (status == "Node Active. Seeking peers...") isRescanning = false
        }

        networkManager.onDeviceConnected = { device ->
            if (connectedDevices.none { it.endpointId == device.endpointId }) {
                connectedDevices.add(device)
                activeMeshNodes[device.name] = System.currentTimeMillis()

                scannedDevices.removeAll { it.endpointId == device.endpointId }
            }
        }

        networkManager.onDeviceDisconnected = { endpointId ->
            connectedDevices.removeAll { it.endpointId == endpointId }
        }

        // Catching the endpointId here to fix the Private Chat!
        networkManager.onMessageReceived = { endpointId, sender, text, isPrivate, isSystem, img, audio ->
            val msgId = UUID.randomUUID().toString()

            if (isSystem) {
                activeMeshNodes[sender] = System.currentTimeMillis()
            } else {
                activeMeshNodes[sender] = System.currentTimeMillis()
                val message = ChatMessage(msgId, sender, text, img, audio, false)

                if (isPrivate) {
                    val log = privateMessages[endpointId] ?: emptyList()
                    privateMessages[endpointId] = log + message
                } else {
                    publicMessages.add(message)
                }
            }
        }
    }

    fun checkHardwareAndGoOnline(context: android.content.Context, customName: String, nodeTag: String) {
        val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager

        val isBluetoothOn = bluetoothAdapter?.isEnabled == true
        val isLocationOn = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        // Note: isWifiEnabled is technically deprecated in newer APIs but still works for basic state checking
        val isWifiOn = wifiManager.isWifiEnabled

        if (!isBluetoothOn || !isLocationOn || !isWifiOn) {
            // HARDWARE IS MISSING: Update UI to tell the user what is wrong
            val missing = mutableListOf<String>()
            if (!isBluetoothOn) missing.add("Bluetooth")
            if (!isLocationOn) missing.add("Location")
            if (!isWifiOn) missing.add("Wi-Fi")

            connectionStatus = "ERROR: Please turn on ${missing.joinToString(", ")} to deploy Mesh Node."
            isOnline = false
        } else {
            // ALL SYSTEMS GO: Fire up the mesh!
            goOnline(customName, nodeTag)
        }
    }

    fun goOnline(customName: String, nodeTag: String) {
        myNodeName = "$customName [$nodeTag]"
        networkManager.myDeviceName = myNodeName
        isOnline = true
        activeMeshNodes.clear()
        networkManager.startMeshNode(teamKey)
    }

    fun goOffline() {
        isOnline = false
        networkManager.stopMeshNode()
        connectedDevices.clear()
        activeMeshNodes.clear()

        scannedDevices.clear()
    }

    fun rescan() {
        isRescanning = true
        networkManager.rescan()
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?) {
        val msgId = UUID.randomUUID().toString()
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("senderName", myNodeName)
            put("text", text)
            put("isPrivate", false)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
        }.toString()

        publicMessages.add(ChatMessage(msgId, "Me", text, imageBase64, audioBase64, true))
        networkManager.broadcastPayload(jsonString)
    }

    fun sendPrivateMessage(target: ConnectedDevice, text: String, imageBase64: String?, audioBase64: String?) {
        val msgId = UUID.randomUUID().toString()
        val jsonString = JSONObject().apply {
            put("id", msgId)
            put("senderName", myNodeName)
            put("text", text)
            put("isPrivate", true)
            put("isSystem", false)
            if (imageBase64 != null) put("image", imageBase64)
            if (audioBase64 != null) put("audio", audioBase64)
        }.toString()

        val message = ChatMessage(msgId, "Me", text, imageBase64, audioBase64, true)
        val currentLog = privateMessages[target.endpointId] ?: emptyList()
        privateMessages[target.endpointId] = currentLog + message

        networkManager.sendDirectPayload(target.endpointId, jsonString)
    }

    private fun startMeshGarbageCollector() {
        viewModelScope.launch {
            while (true) {
                if (isOnline) {
                    val now = System.currentTimeMillis()
                    val deadNodes = activeMeshNodes.filter { (now - it.value) > 15000 }.keys
                    deadNodes.forEach { activeMeshNodes.remove(it) }
                }
                delay(5000)
            }
        }
    }
}