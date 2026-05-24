package com.example.testresqmesh.core.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.NotificationHelper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject

class MeshNetworkManager(private val context: Context) {

    private val notificationHelper = NotificationHelper(context)
    private val connectionsClient = Nearby.getConnectionsClient(context)
    var myDeviceName: String = ""
    private var myPowerScore: Int = 0
    private val pendingNames = mutableMapOf<String, String>()

    // Callbacks to communicate back to the ViewModel
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null

    // THIS IS THE LINE THAT WAS CAUSING THE HEADACHE! (Notice the 11 parameters now)
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String) -> Unit)? = null
    
    // Gossip Protocol SEEN Callback (msgId, readerName)
    var onMessageSeen: ((String, String) -> Unit)? = null
    var onMessageDelivered: ((String, String) -> Unit)? = null

    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null // Added isConnecting flag
    var onDeviceScanRemoved: ((String) -> Unit)? = null

    // Heartbeat & Scanner Handlers
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private val connectedEndpointIds = mutableSetOf<String>()
    // The Bouncer's Memory (Stores IDs of messages we already processed)
    private val seenMessageIds = mutableSetOf<String>()
    
    // Tracks which physical medium (Wi-Fi Direct vs Bluetooth) the endpoint is currently using
    val endpointMedium = mutableMapOf<String, String>()

    private var activeServiceId = "com.example.testresqmesh.p2p.PUBLIC"

    private val activeScannedEndpoints = mutableSetOf<String>()

    private val scanningDelay: Long = 7000
    private val afterDiscoveryDelay: Long = 3000

    fun startMeshNode(teamKey: String) {
        // --- THE "DOUBLE-START JOLT" FIX ---
        // Manually resetting the client before starting ensures a fresh radio state (Fixes "Stale Cache")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()

        val formattedKey = teamKey.trim().uppercase().ifEmpty { "PUBLIC" }
        activeServiceId = "com.example.testresqmesh.p2p.$formattedKey"
        
        // Calculate Power Score to determine who leads the connection
        myPowerScore = calculatePowerScore()
        val advertisingName = "$myPowerScore|$myDeviceName"

        // OPTIMIZATION: High-power advertising for "Fast Pair" experience
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(false) // Maximize radio frequency for faster discovery
            .build()

        connectionsClient.startAdvertising(advertisingName, activeServiceId, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                onStatusChanged?.invoke("Node Active [Room: $formattedKey]. Seeking peers...")
                startNativeScanner()
                startHeartbeat()
            }
            .addOnFailureListener { onStatusChanged?.invoke("Failed to start node.") }
    }

    private fun calculatePowerScore(): Int {
        var score = 10 // Base score
        
        // CPU Processing Power
        val numCores = Runtime.getRuntime().availableProcessors()
        score += (numCores * 5)
        
        // RAM (Memory Capacity)
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRamGB = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
            score += (totalRamGB * 10)
        } catch (e: Exception) {
            AppLogger.d("MeshNetwork", "Failed to read RAM for power score.")
        }
        
        // Operating System (Better background handling)
        val apiLevel = android.os.Build.VERSION.SDK_INT
        if (apiLevel >= 33) score += 20 // Android 13+
        else if (apiLevel >= 31) score += 10 // Android 12
        
        // Bluetooth Advanced Features (Safer check)
        try {
            val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
            if (bluetoothAdapter != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (bluetoothAdapter.isLe2MPhySupported) score += 15
                if (bluetoothAdapter.isLeExtendedAdvertisingSupported) score += 15
            }
        } catch (e: SecurityException) {
            // Permission missing or denied, gracefully ignore.
        }

        AppLogger.d("MeshNetwork", "My Power Score computed: $score")
        return score
    }

    // Smooth continuous native scanner with high-power optimization:
    private fun startNativeScanner() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(false) // High-power mode to find peers instantly
            .build()
        connectionsClient.startDiscovery(activeServiceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { onStatusChanged?.invoke("Seeking peers...") }
            .addOnFailureListener { onStatusChanged?.invoke("Scanner failed.") }
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
        connectionsClient.stopAllEndpoints()
        connectedEndpointIds.clear()
        pendingNames.clear()
        endpointMedium.clear()
        onStatusChanged?.invoke("Offline")
        AppLogger.d("MeshNetwork", "Mesh Node completely stopped.")
    }

    fun disconnectFromEndpoint(endpointId: String) {
        AppLogger.d("MeshNetwork", "TESTING TOOL: Manually disconnecting from physical endpoint -> $endpointId")
        connectionsClient.disconnectFromEndpoint(endpointId)
        connectedEndpointIds.remove(endpointId)
        endpointMedium.remove(endpointId)
        onDeviceDisconnected?.invoke(endpointId)
    }

    fun rescan() {
        onStatusChanged?.invoke("Rescanning nearby area...")
        connectionsClient.stopDiscovery()
        val jitter = kotlin.random.Random.nextLong(100, 600)
        Handler(Looper.getMainLooper()).postDelayed({
            val discoveryOptions = DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .setLowPower(false)
                .build()
            connectionsClient.startDiscovery(activeServiceId, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener { onStatusChanged?.invoke("Node Active. Seeking peers...") }
        }, 1500 + jitter)
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
            AppLogger.d("MeshNetwork_ERROR", "Failed to cache outbound message ID: ${e.message}")
        }
        // ----------------------

        val targets = connectedEndpointIds.filter { it != excludeEndpointId }
        if (targets.isNotEmpty()) {
            AppLogger.d("MeshNetwork", "ROUTE (Broadcast): Flooding message to ${targets.size} physical connections.")
            val payload = com.google.android.gms.nearby.connection.Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(targets.toList(), payload)
        } else {
            AppLogger.d("MeshNetwork", "ROUTE (Broadcast): No other nodes to relay to. Chain stops here.")
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
            AppLogger.d("MeshNetwork_ERROR", "Failed to cache outbound message ID: ${e.message}")
        }
        // ----------------------

        AppLogger.d("MeshNetwork", "ROUTE (Direct): Sending private payload to physical endpoint -> $targetId")
        val payload = com.google.android.gms.nearby.connection.Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(targetId, payload)
    }

    private fun sendSystemPulse() {
        val pulseId = java.util.UUID.randomUUID().toString()
        val jsonString = JSONObject().apply {
            put("id", pulseId)
            put("senderName", myDeviceName)
            put("isSystem", true)
        }.toString()
        broadcastPayload(jsonString)
        // Dummy ID "LOCAL" for your own pulse
        onMessageReceived?.invoke("LOCAL", pulseId, myDeviceName, "", false, true, null, null, null, null, "LOCAL")
    }

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val jsonString = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString()) // Bouncer needs unique ID for the payload itself
            put("type", "SEEN")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
        }.toString()

        if (isPrivate && targetId != null) {
            sendDirectPayload(targetId, jsonString)
        } else {
            broadcastPayload(jsonString)
        }
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val jsonString = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("type", "DELIVERED")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
        }.toString()

        if (isPrivate && targetId != null) {
            sendDirectPayload(targetId, jsonString)
        } else {
            broadcastPayload(jsonString)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Parse the peer's power score and name
            val rawName = info.endpointName
            val parts = rawName.split("|", limit = 2)
            val peerScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cleanPeerName = parts.getOrNull(1) ?: rawName

            // Add them to our physical room tracker
            activeScannedEndpoints.add(endpointId)

            // --- POWER SCORE TIE-BREAKER: The strongest phone leads ---
            val shouldInitiate = if (myPowerScore != peerScore) {
                myPowerScore > peerScore
            } else {
                myDeviceName.compareTo(cleanPeerName) > 0
            }
            
            val myRole = if (shouldInitiate) "INITIATOR" else "RECEIVER"
            onDeviceScanned?.invoke(endpointId, cleanPeerName, peerScore, myRole, false)

            // Don't connect to yourself
            if (cleanPeerName == myDeviceName) return

            if (shouldInitiate) {
                // INITIATOR: We will trigger the connection request. 
                AppLogger.d("MeshNetwork", "I am Initiator for $cleanPeerName")
                attemptConnection(endpointId, cleanPeerName)
            } else {
                // RECEIVER: Patient Fallback Protocol
                // Passively yield for 3 seconds to give the strong phone time to find us.
                val patienceTimer = 3000L + kotlin.random.Random.nextLong(0, 1000)
                AppLogger.d("MeshNetwork", "I am Receiver. Passively yielding to $cleanPeerName for ${patienceTimer}ms...")
                
                Handler(Looper.getMainLooper()).postDelayed({
                    // If the timer expires and we STILL aren't connected, the Initiator must be suffering from a blind scan.
                    if (!connectedEndpointIds.contains(endpointId) && activeScannedEndpoints.contains(endpointId)) {
                        AppLogger.d("MeshNetwork", "Initiator appears blind. Receiver taking over for $endpointId")
                        attemptConnection(endpointId, cleanPeerName)
                    }
                }, patienceTimer)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            activeScannedEndpoints.remove(endpointId)
            onDeviceScanRemoved?.invoke(endpointId)
        }
    }

    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        AppLogger.d("MeshNetwork", "FORCE CONNECT requested for $endpointName ($endpointId)")
        attemptConnection(endpointId, endpointName, 0)
    }

    private fun attemptConnection(endpointId: String, endpointName: String, retryCount: Int = 0) {
        connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                if (connectedEndpointIds.contains(endpointId)) return@addOnFailureListener
                
                // --- SMART BURST RETRY LOGIC ---
                // If retryCount < 3: 1s interval (Rapid Burst)
                // If retryCount >= 3: 8s interval (Lazy Fallback)
                val delay = if (retryCount < 3) 1000L else 8000L
                val jitter = kotlin.random.Random.nextLong(100, 400)
                
                AppLogger.d("MeshNetwork", "Connection to $endpointName failed. Retry #$retryCount in ${delay}ms...")

                Handler(Looper.getMainLooper()).postDelayed({
                    if (activeScannedEndpoints.contains(endpointId) && !connectedEndpointIds.contains(endpointId)) {
                        attemptConnection(endpointId, endpointName, retryCount + 1)
                    }
                }, delay + jitter)
            }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onDeviceScanRemoved?.invoke(endpointId)
            
            // Clean the score prefix from the stored name
            val rawName = info.endpointName
            val parts = rawName.split("|", limit = 2)
            val peerScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cleanName = parts.getOrNull(1) ?: rawName
            
            // otify the UI that an inbound connection is starting, even if not scanned
            onDeviceScanned?.invoke(endpointId, cleanName, peerScore, "CONNECTING", true)
            
            pendingNames[endpointId] = cleanName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
    
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val deviceName = pendingNames[endpointId] ?: "Unknown"
            pendingNames.remove(endpointId) // BUG FIX: Free the memory leak!

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointIds.add(endpointId)
                endpointMedium[endpointId] = "Bluetooth 5.4" // Start with Bluetooth assumption
                onDeviceConnected?.invoke(ConnectedDevice(endpointId, deviceName))
            }
        }
        override fun onDisconnected(endpointId: String) {
            pendingNames.remove(endpointId) // BUG FIX: Ensure clean memory
            endpointMedium.remove(endpointId)
            onDeviceScanRemoved?.invoke(endpointId)
            connectedEndpointIds.remove(endpointId)
            onDeviceDisconnected?.invoke(endpointId)
        }
        
        override fun onBandwidthChanged(endpointId: String, bandwidthInfo: BandwidthInfo) {
            if (bandwidthInfo.quality == BandwidthInfo.Quality.HIGH) {
                endpointMedium[endpointId] = "Wi-Fi Direct"
                AppLogger.d("MeshNetwork", "Bandwidth UPGRADED to Wi-Fi Direct for $endpointId")
            } else {
                endpointMedium[endpointId] = "Bluetooth 5.4"
                AppLogger.d("MeshNetwork", "Bandwidth DOWNGRADED to Bluetooth for $endpointId")
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: com.google.android.gms.nearby.connection.Payload) {
            if (payload.type == com.google.android.gms.nearby.connection.Payload.Type.BYTES) {
                val jsonString = String(payload.asBytes()!!, Charsets.UTF_8)
                processJsonPayload(endpointId, jsonString)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun processJsonPayload(endpointId: String, jsonString: String) {
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

            // --- GOSSIP PROTOCOL SEEN RECEIPTS ---
            if (jsonObject.has("type") && (jsonObject.getString("type") == "SEEN" || jsonObject.getString("type") == "DELIVERED")) {
                val payloadType = jsonObject.getString("type")
                val targetMessageId = jsonObject.getString("targetMessageId")
                val reader = jsonObject.getString("reader")
                val isPrivateReceipt = jsonObject.optBoolean("isPrivate", false)
                
                // Tell the UI that someone saw or received this message!
                if (payloadType == "SEEN") {
                    onMessageSeen?.invoke(targetMessageId, reader)
                } else if (payloadType == "DELIVERED") {
                    onMessageDelivered?.invoke(targetMessageId, reader)
                }
                
                // Gossip: Forward the receipt to everyone else (unless it's private)
                if (!isPrivateReceipt) {
                    broadcastPayload(jsonString, excludeEndpointId = endpointId)
                }
                return
            }

            val sender = jsonObject.getString("senderName")
            val isSystem = jsonObject.optBoolean("isSystem", false)

            if (isSystem) {
                onMessageReceived?.invoke(endpointId, msgId, sender, "", false, true, null, null, null, null, "LOCAL")
                broadcastPayload(jsonString, excludeEndpointId = endpointId)
                return
            }

            val text = jsonObject.getString("text")
            val targetName = jsonObject.optString("targetName", "")
            val isPrivate = jsonObject.optBoolean("isPrivate", false)

            AppLogger.d("MeshNetwork", "ROUTE (Received): Message from [$sender] arrived physically via endpoint [$endpointId] using ${endpointMedium[endpointId] ?: "Bluetooth"}")

            val imageBase64 = if (jsonObject.has("image")) jsonObject.getString("image") else null
            val audioBase64 = if (jsonObject.has("audio")) jsonObject.getString("audio") else null
            val locationLat = if (jsonObject.has("locationLat")) jsonObject.getDouble("locationLat") else null
            val locationLng = if (jsonObject.has("locationLng")) jsonObject.getDouble("locationLng") else null
            val medium = endpointMedium[endpointId] ?: "Bluetooth 5.4"

            if (isPrivate) {
                if (targetName == myDeviceName) {
                    notificationHelper.showPrivateMessageNotification(sender, text)
                    onMessageReceived?.invoke(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium)
                } else {
                    AppLogger.d("MeshNetwork", "ROUTE (Relay): Forwarding Private message meant for [$targetName] securely across the mesh.")
                    broadcastPayload(jsonString, excludeEndpointId = endpointId)
                }
            } else {
                onMessageReceived?.invoke(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium)
                AppLogger.d("MeshNetwork", "ROUTE (Relay): Forwarding public message from [$sender] to all other connected peers.")
                broadcastPayload(jsonString, excludeEndpointId = endpointId)
            }

        } catch (e: Exception) {
            AppLogger.d("MeshNetwork_ERROR", "Parse error: ${e.message}")
        }
    }
}