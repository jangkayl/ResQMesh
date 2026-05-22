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
    
    // NEW: Gossip Protocol SEEN Callback (msgId, readerName)
    var onMessageSeen: ((String, String) -> Unit)? = null
    var onMessageDelivered: ((String, String) -> Unit)? = null

    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null // Added isConnecting flag
    var onDeviceScanRemoved: ((String) -> Unit)? = null

    // Heartbeat & Scanner Handlers
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private val scannerPulseHandler = Handler(Looper.getMainLooper())
    private var scannerPulseRunnable: Runnable? = null

    private val connectedEndpointIds = mutableSetOf<String>()
    // NEW: The Bouncer's Memory (Stores IDs of messages we already processed)
    private val seenMessageIds = mutableSetOf<String>()
    
    // Connection Medium Tracker (Defaults to Bluetooth 5.4 until upgraded)
    private val endpointMedium = mutableMapOf<String, String>()

    // Scanner Failsafe State
    private var isHandshaking = false
    private var handshakeStartTime = 0L

    private var activeServiceId = "com.example.testresqmesh.p2p.PUBLIC"

    private val activeScannedEndpoints = mutableSetOf<String>()

    private val scanningDelay: Long = 7000
    private val afterDiscoveryDelay: Long = 3000

    fun startMeshNode(teamKey: String) {
        // --- THE "DOUBLE-START JOLT" FIX ---
        // Manually resetting the client before starting ensures a fresh radio state (Fixes "Stale Cache")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        // ------------------------------------

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
                startScannerPulse() // NEW: Start the periodic pulse
            }
            .addOnFailureListener { onStatusChanged?.invoke("Failed to start node.") }
    }

    private fun startScannerPulse() {
        scannerPulseRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val isStuck = isHandshaking && (currentTime - handshakeStartTime > 15000)

                if (isStuck) {
                    AppLogger.d("MeshNetwork", "Scanner Pulse: Handshake appears stuck. Forcing failsafe scanner restart!")
                    isHandshaking = false
                }

                // OPTIMIZATION: Pulse every 7 seconds to keep finding new mesh peers
                // We only skip the pulse if we are ACTIVELY in the middle of a healthy handshake.
                if (!isHandshaking) {
                    AppLogger.d("MeshNetwork", "Scanner Pulse: Jolting Radio to find more peers...")
                    connectionsClient.stopDiscovery()
                    startNativeScanner()
                }
                scannerPulseHandler.postDelayed(this, scanningDelay)
            }
        }
        scannerPulseHandler.postDelayed(scannerPulseRunnable!!, scanningDelay)
    }

    private fun calculatePowerScore(): Int {
        var score = 10 // Base score
        
        // 1. CPU Processing Power
        val numCores = Runtime.getRuntime().availableProcessors()
        score += (numCores * 5)
        
        // 2. RAM (Memory Capacity)
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRamGB = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
            score += (totalRamGB * 10)
        } catch (e: Exception) {
            AppLogger.d("MeshNetwork", "Failed to read RAM for power score.")
        }
        
        // 3. Operating System (Better background handling)
        val apiLevel = android.os.Build.VERSION.SDK_INT
        if (apiLevel >= 33) score += 20 // Android 13+
        else if (apiLevel >= 31) score += 10 // Android 12
        
        // 4. Bluetooth Advanced Features (Safer check)
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

    // 1. Smooth continuous native scanner with high-power optimization:
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
        scannerPulseRunnable?.let { scannerPulseHandler.removeCallbacks(it) }
        connectionsClient.stopAllEndpoints()
        connectedEndpointIds.clear()
        pendingNames.clear()
        endpointMedium.clear()
        onStatusChanged?.invoke("Offline")
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
            AppLogger.d("MeshNetwork_ERROR", "Failed to cache outbound message ID: ${e.message}")
        }
        // ----------------------

        val payload = Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
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
                // Passively yield for 10-13 seconds to give the strong phone plenty of time to find us over long distances.
                val patienceTimer = 10000L + kotlin.random.Random.nextLong(0, 3000)
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

    private fun attemptConnection(endpointId: String, endpointName: String, retryCount: Int = 0) {
        // Pause discovery immediately to free up the Bluetooth antenna for the handshake!
        isHandshaking = true
        handshakeStartTime = System.currentTimeMillis()
        connectionsClient.stopDiscovery()

        connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                isHandshaking = false
                if (connectedEndpointIds.contains(endpointId)) return@addOnFailureListener
                
                // Exponential backoff
                val baseDelay = (Math.pow(2.0, retryCount.toDouble()) * 1000).toLong()
                val jitter = kotlin.random.Random.nextLong(200, 800)
                
                // Resume discovery while waiting for backoff, so we aren't completely blind
                startNativeScanner()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (activeScannedEndpoints.contains(endpointId) && !connectedEndpointIds.contains(endpointId)) {
                        attemptConnection(endpointId, endpointName, retryCount + 1)
                    }
                }, baseDelay + jitter)
            }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Receiver also pauses discovery to help the handshake complete smoothly
            isHandshaking = true
            handshakeStartTime = System.currentTimeMillis()
            connectionsClient.stopDiscovery()

            onDeviceScanRemoved?.invoke(endpointId)
            
            // Clean the score prefix from the stored name
            val rawName = info.endpointName
            val parts = rawName.split("|", limit = 2)
            val peerScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cleanName = parts.getOrNull(1) ?: rawName
            
            // NEW: Notify the UI that an inbound connection is starting, even if not scanned
            onDeviceScanned?.invoke(endpointId, cleanName, peerScore, "CONNECTING", true)
            
            pendingNames[endpointId] = cleanName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
    
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            // Whatever the result is, we must resume discovery now that the handshake is over!
            isHandshaking = false
            startNativeScanner()

            val deviceName = pendingNames[endpointId] ?: "Unknown"
            pendingNames.remove(endpointId) // BUG FIX: Free the memory leak!

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointIds.add(endpointId)
                endpointMedium[endpointId] = "Bluetooth 5.4" // Start with Bluetooth assumption
                onDeviceConnected?.invoke(ConnectedDevice(endpointId, deviceName))

                // Force bandwidth upgrade to Wi-Fi Direct using the Dummy Stream Hack
                // Since this API version doesn't support requestBandwidthUpgrade, 
                // sending a stream payload forces the system to spin up Wi-Fi Direct.
                val dummyStream = java.io.ByteArrayInputStream(ByteArray(1))
                connectionsClient.sendPayload(endpointId, com.google.android.gms.nearby.connection.Payload.fromStream(dummyStream))
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

                    // --- 2. GOSSIP PROTOCOL SEEN RECEIPTS ---
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
                    val isPrivate = jsonObject.optBoolean("isPrivate", false)

                    if (isPrivate) {
                        // Fire the local notification alert!
                        notificationHelper.showPrivateMessageNotification(sender, text)
                    }

                    val imageBase64 = if (jsonObject.has("image")) jsonObject.getString("image") else null
                    val audioBase64 = if (jsonObject.has("audio")) jsonObject.getString("audio") else null
                    val locationLat = if (jsonObject.has("locationLat")) jsonObject.getDouble("locationLat") else null
                    val locationLng = if (jsonObject.has("locationLng")) jsonObject.getDouble("locationLng") else null

                    // Determine what medium this message just arrived on
                    val medium = endpointMedium[endpointId] ?: "Bluetooth 5.4"

                    // Passing the exact endpointId right here!
                    onMessageReceived?.invoke(endpointId, msgId, sender, text, isPrivate, false, imageBase64, audioBase64, locationLat, locationLng, medium)

                    if (!isPrivate) {
                        broadcastPayload(jsonString, excludeEndpointId = endpointId)
                    }

                } catch (e: Exception) {
                    AppLogger.d("MeshNetwork_ERROR", "Parse error: ${e.message}")
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}