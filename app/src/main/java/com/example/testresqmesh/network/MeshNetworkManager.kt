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
    private var myPowerScore: Int = 0
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
                // OPTIMIZATION: Pulse every 3 seconds (Aggressive but Stable)
                // Only pulse if we aren't currently in a "Pending" connection or already have peers
                if (connectedEndpointIds.isEmpty() && pendingNames.isEmpty()) {
                    Log.d("MeshNetwork", "Scanner Pulse: Jolting Radio...")
                    connectionsClient.stopDiscovery()
                    startNativeScanner()
                }
                scannerPulseHandler.postDelayed(this, 3000)
            }
        }
        scannerPulseHandler.postDelayed(scannerPulseRunnable!!, 3000)
    }

    private fun calculatePowerScore(): Int {
        var score = 10 // Base score
        
        // 1. API Level (Modern Android = Better P2P handling)
        val apiLevel = android.os.Build.VERSION.SDK_INT
        if (apiLevel >= 33) score += 40
        else if (apiLevel >= 31) score += 30
        else if (apiLevel >= 29) score += 20
        
        // 2. Bluetooth Capabilities
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
        if (bluetoothAdapter != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (bluetoothAdapter.isLe2MPhySupported) score += 15
                if (bluetoothAdapter.isLeExtendedAdvertisingSupported) score += 15
            }
        }
        
        // 3. Battery Level (Stable power = better hub)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryPct = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        if (batteryPct > 50) score += 10
        else if (batteryPct > 20) score += 5

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
            // Parse the peer's power score and name
            val rawName = info.endpointName
            val parts = rawName.split("|", limit = 2)
            val peerScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cleanPeerName = parts.getOrNull(1) ?: rawName

            // Add them to our physical room tracker
            activeScannedEndpoints.add(endpointId)
            onDeviceScanned?.invoke(endpointId, cleanPeerName)

            // Don't connect to yourself
            if (cleanPeerName == myDeviceName) return

            // --- POWER SCORE TIE-BREAKER: The strongest phone leads ---
            val shouldInitiate = if (myPowerScore != peerScore) {
                myPowerScore > peerScore
            } else {
                myDeviceName.compareTo(cleanPeerName) > 0
            }

            if (shouldInitiate) {
                // PRIMARY (The Boss): We are stronger. 
                Log.d("MeshNetwork", "I am Primary (Score $myPowerScore) for $cleanPeerName (Score $peerScore)")
                connectionsClient.stopDiscovery()
                attemptConnection(endpointId, cleanPeerName)
            } else {
                // BACKUP (Yielding): We are weaker. Wait for them to call us.
                val jitter = kotlin.random.Random.nextLong(0, 500)
                Log.d("MeshNetwork", "I am Backup (Score $myPowerScore). Yielding to $cleanPeerName (Score $peerScore)...")
                
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!connectedEndpointIds.contains(endpointId) && activeScannedEndpoints.contains(endpointId)) {
                        Log.d("MeshNetwork", "Primary failed. Backup taking over for $endpointId")
                        connectionsClient.stopDiscovery()
                        attemptConnection(endpointId, cleanPeerName)
                    }
                }, 1200 + jitter)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            activeScannedEndpoints.remove(endpointId)
            onDeviceScanRemoved?.invoke(endpointId)
        }
    }

    private fun attemptConnection(endpointId: String, endpointName: String) {
        connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                if (connectedEndpointIds.contains(endpointId)) return@addOnFailureListener
                
                val jitter = kotlin.random.Random.nextLong(200, 800)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (activeScannedEndpoints.contains(endpointId) && !connectedEndpointIds.contains(endpointId)) {
                        attemptConnection(endpointId, endpointName)
                    }
                }, 1000 + jitter)
            }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onDeviceScanRemoved?.invoke(endpointId)
            
            // Clean the score prefix from the stored name
            val rawName = info.endpointName
            val cleanName = if (rawName.contains("|")) rawName.split("|")[1] else rawName
            
            pendingNames[endpointId] = cleanName
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
    
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val deviceName = pendingNames[endpointId] ?: "Unknown"
                connectedEndpointIds.add(endpointId)
                onDeviceConnected?.invoke(ConnectedDevice(endpointId, deviceName))

                // TRIGGER BANDWIDTH UPGRADE: Sending a small dummy payload immediately 
                // tells Google Nearby to switch from Bluetooth to high-speed Wi-Fi.
                val triggerPayload = JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("senderName", myDeviceName)
                    put("isSystem", true)
                    put("type", "bandwidth_upgrade_trigger")
                }.toString()
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(triggerPayload.toByteArray()))
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