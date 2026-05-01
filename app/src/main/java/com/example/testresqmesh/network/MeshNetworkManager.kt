package com.example.testresqmesh.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.testresqmesh.data.models.ConnectedDevice
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject

class MeshNetworkManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)

    var myDeviceName: String = ""
    private val pendingNames = mutableMapOf<String, String>()

    // Callbacks to communicate back to the ViewModel
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null

    // THIS IS THE LINE THAT WAS CAUSING THE HEADACHE! (Notice the 7 Strings now)
    var onMessageReceived: ((String, String, String, Boolean, Boolean, String?, String?) -> Unit)? = null

    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null

    // Heartbeat & Scanner Handlers
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private val scannerPulseHandler = Handler(Looper.getMainLooper())
    private var scannerPulseRunnable: Runnable? = null

    private val connectedEndpointIds = mutableSetOf<String>()
    private var activeServiceId = "com.example.testresqmesh.p2p.PUBLIC"

    fun startMeshNode(teamKey: String) {
        val formattedKey = teamKey.trim().uppercase().ifEmpty { "PUBLIC" }
        activeServiceId = "com.example.testresqmesh.p2p.$formattedKey"

        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(myDeviceName, activeServiceId, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                onStatusChanged?.invoke("Node Active [Room: $formattedKey]. Seeking peers...")
                startAggressiveScanner()
                startHeartbeat()
            }
            .addOnFailureListener { onStatusChanged?.invoke("Failed to start node.") }
    }

    private fun startAggressiveScanner() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        scannerPulseRunnable = object : Runnable {
            override fun run() {
                connectionsClient.stopDiscovery()
                Handler(Looper.getMainLooper()).postDelayed({
                    connectionsClient.startDiscovery(activeServiceId, endpointDiscoveryCallback, discoveryOptions)
                }, 1000)
                scannerPulseHandler.postDelayed(this, 12000)
            }
        }
        scannerPulseHandler.post(scannerPulseRunnable!!)
    }

    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                sendSystemPulse()
                heartbeatHandler.postDelayed(this, 5000)
            }
        }
        heartbeatHandler.post(heartbeatRunnable!!)
    }

    fun stopMeshNode() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        scannerPulseRunnable?.let { scannerPulseHandler.removeCallbacks(it) }
        connectionsClient.stopAllEndpoints()
        connectedEndpointIds.clear()
        pendingNames.clear()
        onStatusChanged?.invoke("Offline")
    }

    fun rescan() {
        onStatusChanged?.invoke("Rescanning nearby area...")
        connectionsClient.stopDiscovery()
        Handler(Looper.getMainLooper()).postDelayed({
            val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            connectionsClient.startDiscovery(activeServiceId, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener { onStatusChanged?.invoke("Node Active. Seeking peers...") }
        }, 1500)
    }

    fun broadcastPayload(jsonString: String, excludeEndpointId: String? = null) {
        val targets = connectedEndpointIds.filter { it != excludeEndpointId }
        if (targets.isNotEmpty()) {
            val payload = Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(targets.toList(), payload)
        }
    }

    fun sendDirectPayload(targetId: String, jsonString: String) {
        val payload = Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(targetId, payload)
    }

    private fun sendSystemPulse() {
        val jsonString = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("senderName", myDeviceName)
            put("isSystem", true)
        }.toString()
        broadcastPayload(jsonString)
        // Dummy ID "LOCAL" for your own pulse
        onMessageReceived?.invoke("LOCAL", myDeviceName, "", false, true, null, null)
    }

    // --- NEARBY CALLBACKS ---
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onDeviceScanned?.invoke(endpointId, info.endpointName)

            if (info.endpointName == myDeviceName) return
            connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { Log.d("MeshNetwork", "Collision handled safely.") }
        }
        override fun onEndpointLost(endpointId: String) {
            onDeviceScanRemoved?.invoke(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onDeviceScanRemoved?.invoke(endpointId)
            pendingNames[endpointId] = info.endpointName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val deviceName = pendingNames[endpointId] ?: "Unknown"
                connectedEndpointIds.add(endpointId)
                onDeviceConnected?.invoke(ConnectedDevice(endpointId, deviceName))
            }
        }

        override fun onDisconnected(endpointId: String) {
            onDeviceScanRemoved?.invoke(endpointId)
            connectedEndpointIds.remove(endpointId)
            onDeviceDisconnected?.invoke(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val jsonString = String(payload.asBytes()!!, Charsets.UTF_8)
                try {
                    val jsonObject = JSONObject(jsonString)
                    val sender = jsonObject.getString("senderName")
                    val isSystem = jsonObject.optBoolean("isSystem", false)

                    if (isSystem) {
                        onMessageReceived?.invoke(endpointId, sender, "", false, true, null, null)
                        broadcastPayload(jsonString, excludeEndpointId = endpointId)
                        return
                    }

                    val text = jsonObject.getString("text")
                    val isPrivate = jsonObject.optBoolean("isPrivate", false)
                    val imageBase64 = if (jsonObject.has("image")) jsonObject.getString("image") else null
                    val audioBase64 = if (jsonObject.has("audio")) jsonObject.getString("audio") else null

                    // Passing the exact endpointId right here!
                    onMessageReceived?.invoke(endpointId, sender, text, isPrivate, false, imageBase64, audioBase64)

                    if (!isPrivate) {
                        broadcastPayload(jsonString, excludeEndpointId = endpointId)
                    }

                } catch (e: Exception) {
                    Log.e("MeshNetwork", "Parse error", e)
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}