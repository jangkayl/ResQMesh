package com.example.testresqmesh.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.testresqmesh.data.models.ConnectedDevice
import com.example.testresqmesh.utils.NotificationHelper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject

class MeshNetworkManager(private val context: Context) {

    private val notificationHelper = NotificationHelper(context)

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
    // NEW: The Bouncer's Memory (Stores IDs of messages we already processed)
    private val seenMessageIds = mutableSetOf<String>()

    private var activeServiceId = "com.example.testresqmesh.p2p.PUBLIC"

    private val activeScannedEndpoints = mutableSetOf<String>()

    fun startMeshNode(teamKey: String) {
        val formattedKey = teamKey.trim().uppercase().ifEmpty { "PUBLIC" }
        activeServiceId = "com.example.testresqmesh.p2p.$formattedKey"
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(myDeviceName, activeServiceId, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                onStatusChanged?.invoke("Node Active [Room: $formattedKey]. Seeking peers...")
                startNativeScanner() // <--- UPDATE THIS LINE
                startHeartbeat()
            }
            .addOnFailureListener { onStatusChanged?.invoke("Failed to start node.") }
    }

    // 1. DELETE startAggressiveScanner() and REPLACE with this smooth continuous native scanner:
    private fun startNativeScanner() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(activeServiceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { onStatusChanged?.invoke("Node Active. Seeking peers continuously...") }
            .addOnFailureListener { onStatusChanged?.invoke("Scanner failed to start.") }
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
        // --- THE SENDER FIX ---
        // Add our OWN message ID to the memory cache before sending it out
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("id")) {
                val msgId = jsonObject.getString("id")
                seenMessageIds.add(msgId)
            }
        } catch (e: Exception) {
            Log.e("MeshNetwork", "Failed to cache outbound message ID", e)
        }
        // ----------------------

        val targets = connectedEndpointIds.filter { it != excludeEndpointId }
        if (targets.isNotEmpty()) {
            val payload = Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(targets.toList(), payload)
        }
    }

    fun sendDirectPayload(targetId: String, jsonString: String) {
        // --- THE SENDER FIX ---
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("id")) {
                val msgId = jsonObject.getString("id")
                seenMessageIds.add(msgId)
            }
        } catch (e: Exception) {
            Log.e("MeshNetwork", "Failed to cache outbound message ID", e)
        }
        // ----------------------

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

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Add them to our physical room tracker
            activeScannedEndpoints.add(endpointId)
            onDeviceScanned?.invoke(endpointId, info.endpointName)

            // Don't connect to yourself
            if (info.endpointName == myDeviceName) return

            // --- FASTER TIE-BREAKER ---
            // We compare the two names alphabetically.
            // Only the "greater" name is allowed to initiate the connection!
            if (myDeviceName.compareTo(info.endpointName) > 0) {
                Log.d("MeshNetwork", "Tie-Breaker: I am initiating connection to ${info.endpointName}")
                attemptConnection(endpointId, info.endpointName)
            } else {
                Log.d("MeshNetwork", "Tie-Breaker: Waiting for ${info.endpointName} to connect to me.")
                // The Impatient Loser Fallback: Wait 2.5s, if not connected, initiate anyway!
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!connectedEndpointIds.contains(endpointId) && activeScannedEndpoints.contains(endpointId)) {
                        Log.d("MeshNetwork", "Tie-Breaker Fallback: They took too long. I am initiating!")
                        attemptConnection(endpointId, info.endpointName)
                    }
                }, 2500)
            }
            // ---------------------------
        }

        override fun onEndpointLost(endpointId: String) {
            activeScannedEndpoints.remove(endpointId)
            onDeviceScanRemoved?.invoke(endpointId)
        }
    }

    // NEW: The Polite Auto-Retry Function
    private fun attemptConnection(endpointId: String, endpointName: String) {
        connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener {
                Log.d("MeshNetwork", "Handshake failed with $endpointName. Retrying in 1 second...")

                Handler(Looper.getMainLooper()).postDelayed({
                    // ZOMBIE FIX: Are they still physically in the room?
                    val stillInRoom = activeScannedEndpoints.contains(endpointId)

                    // Are they NOT connected yet?
                    val notConnected = !connectedEndpointIds.contains(endpointId)

                    // Only fire the retry if BOTH are true!
                    if (stillInRoom && notConnected) {
                        Log.d("MeshNetwork", "Target still here. Retrying connection to $endpointName...")
                        attemptConnection(endpointId, endpointName)
                    } else {
                        Log.d("MeshNetwork", "Target left or already connected. Canceling retry.")
                    }
                }, 1000)
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

                    // --- 1. THE BOUNCER LOGIC ---
                    val msgId = jsonObject.getString("id")

                    if (seenMessageIds.contains(msgId)) {
                        // We already saw this exact message. Drop it so it doesn't spam!
                        return
                    }

                    // It's a new message! Add it to memory.
                    seenMessageIds.add(msgId)

                    // Keep memory clean so the app doesn't crash after 500 messages
                    if (seenMessageIds.size > 500) {
                        seenMessageIds.remove(seenMessageIds.first())
                    }
                    // ----------------------------

                    val sender = jsonObject.getString("senderName")
                    val isSystem = jsonObject.optBoolean("isSystem", false)

                    if (isSystem) {
                        onMessageReceived?.invoke(endpointId, sender, "", false, true, null, null)
                        broadcastPayload(jsonString, excludeEndpointId = endpointId)
                        return
                    }

                    val text = jsonObject.getString("text")
                    val isPrivate = jsonObject.optBoolean("isPrivate", false)

                    if (isPrivate) {
                        // Fire the local notification alert!
                        notificationHelper.showPrivateMessageNotification(sender, text)
                    }

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