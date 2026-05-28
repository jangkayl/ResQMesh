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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MeshNetworkManager(private val context: Context) {

    private val notificationHelper = NotificationHelper(context)
    private val connectionsClient = Nearby.getConnectionsClient(context)
    var myDeviceName: String = ""
    private var myPowerScore: Int = 0
    private val pendingNames = mutableMapOf<String, String>()

    // Callbacks to communicate back to the ViewModel
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onConnectionFailed: ((String) -> Unit)? = null

    
    // THIS IS THE LINE THAT WAS CAUSING THE HEADACHE! (Notice the 12 parameters now)
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>) -> Unit)? = null
    
    // Gossip Protocol SEEN Callback (msgId, readerName)
    var onMessageSeen: ((String, String) -> Unit)? = null
    var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    
    // Routing Table / Topology Callback
    var onRoutingTableReceived: ((String, List<String>) -> Unit)? = null
    var onPublicKeyReceived: ((String, String) -> Unit)? = null // For E2EE

    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceScanned: ((String, String, Int, String, Boolean) -> Unit)? = null // Added isConnecting flag
    var onDeviceScanRemoved: ((String) -> Unit)? = null

    // Heartbeat & Scanner Handlers
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private val connectedEndpointIds = mutableSetOf<String>()
    private val connectedEndpointNames = mutableMapOf<String, String>()
    
    private val _blockedDeviceNames = MutableStateFlow<Set<String>>(emptySet())
    val blockedDeviceNames: StateFlow<Set<String>> = _blockedDeviceNames.asStateFlow()
    
    // The Bouncer's Memory (Stores IDs of messages we already processed)
    private val seenMessageIds = java.util.LinkedHashSet<String>()
    
    // Tracks if the node is currently running to prevent zombie connection retries
    private val isNodeActive = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Sequential Queue System to prevent "Thundering Herd" Bluetooth collisions
    private val connectionQueue = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, String>>()
    private val isConnectingLock = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Tracks which physical medium (Wi-Fi Direct vs Bluetooth) the endpoint is currently using
    val endpointMedium = mutableMapOf<String, String>()

    private var activeServiceId = "com.example.testresqmesh.p2p.PUBLIC"

    private val activeScannedEndpoints = mutableSetOf<String>()

    private val scanningDelay: Long = 7000
    private val afterDiscoveryDelay: Long = 3000

    private val payloadDispatcher = PayloadDispatcher(object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getSeenMessageIds() = seenMessageIds
        override fun getEndpointMedium(endpointId: String) = endpointMedium[endpointId] ?: "Bluetooth 5.4"
        override fun getConnectedEndpointIdByName(name: String) = connectedEndpointNames.entries.find { it.value == name }?.key
        override fun sendDirectPayload(endpointId: String, payload: String) = this@MeshNetworkManager.sendDirectPayload(endpointId, payload)
        override fun broadcastPayload(payload: String, excludeEndpointId: String?) = this@MeshNetworkManager.broadcastPayload(payload, excludeEndpointId)
        override fun onMessageSeen(msgId: String, readerName: String) { onMessageSeen?.invoke(msgId, readerName) }
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
        override fun onPublicKeyReceived(senderName: String, key: String) { onPublicKeyReceived?.invoke(senderName, key) }
        override fun onRoutingTableReceived(senderName: String, connectedNodes: List<String>) { onRoutingTableReceived?.invoke(senderName, connectedNodes) }
        override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>) {
            this@MeshNetworkManager.onMessageReceived?.invoke(endpointId, msgId, senderName, text, isPrivate, isSystem, imageBase64, audioBase64, locationLat, locationLng, medium, routePath)
        }
        override fun onSosCancelled() { this@MeshNetworkManager.onSosCancelled?.invoke() }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    })

    fun startMeshNode(teamKey: String) {
        isNodeActive.set(true)
        
        // --- THE "DEEP CACHE WIPE" FIX ---
        // Violently drop all zombie sockets and background scanners left alive by Android OS
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        
        // Wipe all memory arrays to simulate a 100% clean Bluetooth restart
        connectedEndpointIds.clear()
        pendingNames.clear()
        endpointMedium.clear()
        activeScannedEndpoints.clear()
        connectedEndpointNames.clear()

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

        // Concurrent Dual-Radio Startup: Start Advertising and Discovery simultaneously
        connectionsClient.startAdvertising(advertisingName, activeServiceId, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                onStatusChanged?.invoke("Node Active [Room: $formattedKey]. Seeking peers...")
                startHeartbeat()
            }
            .addOnFailureListener { onStatusChanged?.invoke("Failed to start node.") }
            
        startNativeScanner()
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
        isNodeActive.set(false)
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        connectionsClient.stopAllEndpoints()
        connectedEndpointIds.clear()
        pendingNames.clear()
        endpointMedium.clear()
        activeScannedEndpoints.clear()
        connectedEndpointNames.clear()
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

    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {
        val currentBlocks = _blockedDeviceNames.value.toMutableSet()
        if (currentBlocks.contains(deviceName)) return
        currentBlocks.add(deviceName)
        _blockedDeviceNames.value = currentBlocks
        
        AppLogger.d("MeshNetwork_PAIRING", "BLOCKED DEVICE: $deviceName. Disconnecting any active sessions.")
        
        // Find and disconnect any active sessions with this device
        val activeIds = connectedEndpointNames.filterValues { it == deviceName }.keys
        for (id in activeIds) {
            if (sendNotification) {
                val blockPayload = JSONObject().apply {
                    put("type", "BLOCK_NOTIFICATION")
                    put("name", myDeviceName)
                }
                connectionsClient.sendPayload(id, Payload.fromBytes(blockPayload.toString().toByteArray()))
                Handler(Looper.getMainLooper()).postDelayed({
                    disconnectFromEndpoint(id)
                }, 300) // Delay to ensure payload is sent before socket closes
            } else {
                disconnectFromEndpoint(id)
            }
        }
        onStatusChanged?.invoke("Blocked: $deviceName")
    }

    fun unblockDevice(deviceName: String) {
        val currentBlocks = _blockedDeviceNames.value.toMutableSet()
        currentBlocks.remove(deviceName)
        _blockedDeviceNames.value = currentBlocks
        
        AppLogger.d("MeshNetwork_PAIRING", "UNBLOCKED DEVICE: $deviceName.")
        onStatusChanged?.invoke("Unblocked: $deviceName")
        rescan() // Trigger a rescan to immediately find them again
    }



    fun rescan() {
        onStatusChanged?.invoke("Rescanning nearby area...")
        AppLogger.d("MeshNetwork_PAIRING", "REFRESHING THE SCANNER (Discovery Only)...")
        connectionsClient.stopDiscovery()
        
        val jitter = kotlin.random.Random.nextLong(100, 600)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isNodeActive.get()) return@postDelayed
            
            // Restart Discovery
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
//            AppLogger.d("MeshNetwork_Routing", "ROUTE (Broadcast): Flooding message to ${targets.size} physical connections.")
            val payload = com.google.android.gms.nearby.connection.Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
            connectionsClient.sendPayload(targets.toList(), payload)
        } else {
            // Use a verbose/routing specific tag so it doesn't spam the main console
//            AppLogger.d("MeshNetwork_Routing", "ROUTE (Broadcast): No other nodes to relay to. Chain stops here.")
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

        AppLogger.d("MeshNetwork_ROUTING", "ROUTE (Direct): Sending private payload to physical endpoint -> $targetId")
        val payload = com.google.android.gms.nearby.connection.Payload.fromBytes(jsonString.toByteArray(Charsets.UTF_8))
        connectionsClient.sendPayload(targetId, payload)
    }

    private fun sendSystemPulse() {
        val pulseId = java.util.UUID.randomUUID().toString()
        val nodesArray = org.json.JSONArray(connectedEndpointNames.values.toList())
        
        val jsonString = JSONObject().apply {
            put("id", pulseId)
            put("senderName", myDeviceName)
            put("isSystem", true)
            put("connectedNodes", nodesArray)
            put("publicKey", CryptoManager.getMyPublicKeyBase64()) // Share RSA Public Key
        }.toString()
        broadcastPayload(jsonString)
    }

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val jsonString = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString()) // Bouncer needs unique ID for the payload itself
            put("type", "SEEN")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
        }.toString()

        // Send it via broadcast so it can traverse the mesh if needed
        broadcastPayload(jsonString)
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null, directedReturnRoute: List<String> = emptyList()) {
        val jsonString = JSONObject().apply {
            put("id", java.util.UUID.randomUUID().toString())
            put("type", "DELIVERED")
            put("targetMessageId", targetMessageId)
            put("reader", myDeviceName)
            put("isPrivate", isPrivate)
            put("returnRoute", org.json.JSONArray().apply { put(myDeviceName) })
            if (directedReturnRoute.isNotEmpty()) {
                put("directedRoute", org.json.JSONArray(directedReturnRoute))
            }
        }.toString()

        if (isPrivate && directedReturnRoute.size > 1) {
            val nextHopName = directedReturnRoute[1]
            val nextHopEndpointId = connectedEndpointNames.entries.find { it.value == nextHopName }?.key
            if (nextHopEndpointId != null) {
                sendDirectPayload(nextHopEndpointId, jsonString)
            } else {
                broadcastPayload(jsonString)
            }
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

            // Don't connect to yourself OR detect yourself in the list
            if (cleanPeerName == myDeviceName) return

            // Add them to our physical room tracker
            activeScannedEndpoints.add(endpointId)

            // --- AGGRESSIVE MODE (Commented Out) ---
            // Bypass power scoring and always initiate connection. Faster, but causes collisions in large groups.
//             val shouldInitiate = true

            // --- SMART TIE-BREAKER ALGORITHM (Active) ---
            // Solves Simultaneous Open Collisions
            val shouldInitiate = if (myPowerScore != peerScore) {
                myPowerScore > peerScore
            } else {
                myDeviceName.compareTo(cleanPeerName) > 0
            }
            
            val myRole = if (shouldInitiate) "INITIATOR" else "RECEIVER"
            onDeviceScanned?.invoke(endpointId, cleanPeerName, peerScore, myRole, false)

            if (shouldInitiate) {
                AppLogger.d("MeshNetwork_PAIRING", "SMART TIE-BREAKER: I am Initiator for $cleanPeerName")
                connectionQueue.add(Pair(endpointId, cleanPeerName))
                processNextConnection()
            } else {
                AppLogger.d("MeshNetwork_PAIRING", "SMART TIE-BREAKER: I am Receiver. Yielding to $cleanPeerName...")
                
                // THE FAILSAFE BUG FIX
                // Wait 12 seconds for socket to establish (increased for sequential queue).
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isNodeActive.get()) return@postDelayed
                    // Only forcefully initiate if we are NOT fully connected AND NOT currently negotiating a connection
                    if (!connectedEndpointIds.contains(endpointId) && !pendingNames.containsKey(endpointId) && activeScannedEndpoints.contains(endpointId)) {
                        AppLogger.d("MeshNetwork_PAIRING", "Impatient Receiver Failsafe triggered for $cleanPeerName!")
                        connectionQueue.add(Pair(endpointId, cleanPeerName))
                        processNextConnection()
                    }
                }, 12000)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            activeScannedEndpoints.remove(endpointId)
            onDeviceScanRemoved?.invoke(endpointId)
        }
    }

    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        AppLogger.d("MeshNetwork_PAIRING", "FORCE CONNECT requested for $endpointName ($endpointId)")
        connectionQueue.add(Pair(endpointId, endpointName))
        processNextConnection()
    }

    private fun processNextConnection() {
        if (!isNodeActive.get()) {
            isConnectingLock.set(false)
            return
        }
        
        // Mutex lock to ensure only ONE connection runs at a time
        if (!isConnectingLock.compareAndSet(false, true)) {
            return // Someone else is currently connecting
        }
        
        val nextDevice = connectionQueue.poll()
        if (nextDevice == null) {
            isConnectingLock.set(false) // Queue is empty, unlock
            return
        }
        
        val endpointId = nextDevice.first
        val endpointName = nextDevice.second

        // Skip conditions
        if (connectedEndpointIds.contains(endpointId) || 
            connectedEndpointNames.containsValue(endpointName) || 
            _blockedDeviceNames.value.contains(endpointName) || 
            pendingNames.containsKey(endpointId) || 
            !activeScannedEndpoints.contains(endpointId)) {
            
            AppLogger.d("MeshNetwork_PAIRING", "Skipping queued connection to $endpointName (Already connected/blocked/lost/pending).")
            isConnectingLock.set(false)
            processNextConnection() // Process next immediately
            return
        }

        // Tiny jitter just for safety against OS lag
        val jitter = kotlin.random.Random.nextLong(100, 500)
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isNodeActive.get() || connectedEndpointIds.contains(endpointId)) {
                isConnectingLock.set(false)
                processNextConnection()
                return@postDelayed
            }
            
            AppLogger.d("MeshNetwork_PAIRING", "Executing sequential connection request to $endpointName")
            connectionsClient.requestConnection(myDeviceName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->    
                    AppLogger.d("MeshNetwork_ERROR", "requestConnection failed immediately: ${e.message}")
                    
                    // Put it back at the end of the queue for an asynchronous retry
                    if (activeScannedEndpoints.contains(endpointId)) {
                        AppLogger.d("MeshNetwork_PAIRING", "Re-queuing $endpointName for later retry.")
                        // Small delay before putting it back into the queue to prevent infinite fast-loops if it's the only device
                        Handler(Looper.getMainLooper()).postDelayed({
                            connectionQueue.add(Pair(endpointId, endpointName))
                            processNextConnection()
                        }, 2000)
                    }
                    
                    // Important: Unlock the queue so the NEXT device can be processed!
                    isConnectingLock.set(false)
                    processNextConnection()
                }
        }, jitter)
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            onDeviceScanRemoved?.invoke(endpointId)
            
            // Clean the score prefix from the stored name
            val rawName = info.endpointName
            val parts = rawName.split("|", limit = 2)
            val peerScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val cleanName = parts.getOrNull(1) ?: rawName
            
            // Store the name IMMEDIATELY so that if we reject it, onConnectionResult still knows who it was
            pendingNames[endpointId] = cleanName
            
            // Notify the UI that an inbound connection is starting, even if not scanned
            onDeviceScanned?.invoke(endpointId, cleanName, peerScore, "CONNECTING", true)
            
            if (_blockedDeviceNames.value.contains(cleanName)) {
                AppLogger.d("MeshNetwork_PAIRING", "REJECTING incoming connection from BLOCKED device: $cleanName")
                connectionsClient.rejectConnection(endpointId)
                return
            }
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
    
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val deviceName = pendingNames[endpointId] ?: "Unknown"
            pendingNames.remove(endpointId) // BUG FIX: Free the memory leak!

            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                connectedEndpointIds.add(endpointId)
                connectedEndpointNames[endpointId] = deviceName
                endpointMedium[endpointId] = "Bluetooth 5.4" // Start with Bluetooth assumption
                onDeviceConnected?.invoke(ConnectedDevice(endpointId, deviceName))
                
                // Unlock the queue so the NEXT device can be processed!
                isConnectingLock.set(false)
                processNextConnection()
            } else if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                AppLogger.d("MeshNetwork_PAIRING", "Connection explicitly REJECTED by peer. Giving up peacefully.")
                
                // If we got rejected, immediately auto-block them so the Block becomes fully Mutual!
                blockDevice(deviceName, sendNotification = false)
                
                // Clear the SYNCING flag without wiping their saved Power Score/Role
                onConnectionFailed?.invoke(endpointId)
                
                // Unlock the queue so the NEXT device can be processed!
                isConnectingLock.set(false)
                processNextConnection()
            } else {
                AppLogger.d("MeshNetwork_PAIRING", "Connection to $deviceName failed with status ${result.status.statusCode}")
                
                // Clear the SYNCING flag without wiping their saved Power Score/Role
                onConnectionFailed?.invoke(endpointId)
                
                // THE "PERMANENT GIVE-UP" BUG FIX: Asynchronous Retry Loop
                if (activeScannedEndpoints.contains(endpointId)) {
                    val delay = kotlin.random.Random.nextLong(2000, 5000) // Random backoff to prevent ping-pong storms
                    AppLogger.d("MeshNetwork_PAIRING", "Queuing asynchronous retry for $deviceName in ${delay}ms...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isNodeActive.get()) return@postDelayed
                        connectionQueue.add(Pair(endpointId, deviceName))
                        processNextConnection()
                    }, delay)
                }
                
                // Unlock the queue so the NEXT device can be processed!
                isConnectingLock.set(false)
                processNextConnection()
            }
        }

        override fun onDisconnected(endpointId: String) {
            AppLogger.d("MeshNetwork_PAIRING", "Disconnected from endpoint: $endpointId")
            
            // Grab the name before we delete it to check if they are blocked
            val deviceName = connectedEndpointNames[endpointId] ?: pendingNames[endpointId]
            
            pendingNames.remove(endpointId) // BUG FIX: Ensure clean memory
            endpointMedium.remove(endpointId)
            activeScannedEndpoints.remove(endpointId) // BUG FIX: Purge Ghost IDs to prevent Jitter Slowdown
            onDeviceScanRemoved?.invoke(endpointId)
            connectedEndpointIds.remove(endpointId)
            connectedEndpointNames.remove(endpointId)
            onDeviceDisconnected?.invoke(endpointId)
            
            // Auto-Rescan to flush the BLE MAC Cache (does NOT drop other active connections!)
            if (isNodeActive.get()) {
                if (deviceName != null && _blockedDeviceNames.value.contains(deviceName)) {
                    AppLogger.d("MeshNetwork_PAIRING", "Skipping Auto-Rescan because $deviceName is BLOCKED to prevent infinite connection loops.")
                } else {
                    AppLogger.d("MeshNetwork_PAIRING", "Triggering Auto-Rescan to flush BLE cache for dropped endpoint...")
                    rescan()
                }
            }
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
            val json = JSONObject(jsonString)
            if (json.has("type") && json.getString("type") == "BLOCK_NOTIFICATION") {
                val blockerName = json.getString("name")
                AppLogger.d("MeshNetwork_PAIRING", "Received BLOCK_NOTIFICATION from $blockerName. Automatically blocking them.")
                blockDevice(blockerName, sendNotification = false)
                return
            }
        } catch (e: Exception) {
            AppLogger.d("MeshNetwork_ERROR", "Failed to parse potential control payload: ${e.message}")
        }
        payloadDispatcher.dispatch(endpointId, jsonString)
    }
}