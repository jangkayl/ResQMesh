package com.example.testresqmesh.core.network

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy
import com.example.testresqmesh.core.network.bluetooth.state.GattTransfer
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferFlight
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatChallenge
import com.example.testresqmesh.core.network.bluetooth.state.HandshakeRadioGate
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.NotificationHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.ByteBuffer
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@SuppressLint("MissingPermission")
class NativeBleManager(val context: Context) {
    val gattServerManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattServerManager(context, this)
    val gattClientManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattClientManager(context, this)
    val store = com.example.testresqmesh.core.network.bluetooth.state.BleStateStore()
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onDeviceLivenessChanged: ((String, Boolean) -> Unit)? = null
    var onDeviceScanned: ((com.example.testresqmesh.core.model.ScanEvent) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null
    var onMessageReceived: ((String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>, String) -> Unit)? = null
    var onMessageSeen: ((String, String) -> Unit)? = null
    var stpNeighborsProvider: (() -> Set<String>)? = null
    var onLiveAudioChunk: ((String, String, ByteArray) -> Unit)? = null
    var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    var onPublicKeyReceived: ((String, String, String) -> Unit)? = null
    var onRoutingTableReceived: ((String, List<String>) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null
    var checkRouteExists: ((String) -> Boolean)? = null

    var myDeviceName: String = "ResQMesh_Node"
    val myHex = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()

    /**
     * Stable, persisted node ID (SharedPreferences `node_id`) supplied by [MeshRepository.startNode].
     * Advertised as its own field so peer identity survives display-name truncation.
     */
    var myNodeId: String = ""

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val bleAdvertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
    val bleScanner get() = bluetoothAdapter?.bluetoothLeScanner
    private val scanActive = AtomicBoolean(false)
    private val handshakeRadioGate = HandshakeRadioGate()

    val SERVICE_UUID = UUID.fromString("B9A34F5C-7462-4C61-8935-7C2D4A15A3E4") // ResQMesh Custom Service
    val RX_CHARACTERISTIC_UUID = UUID.fromString("6A81C2E5-309F-4D88-B270-4A9A65D8B6C7")
    val TX_CHARACTERISTIC_UUID = UUID.fromString("1E4D9C7B-6F2A-4B9E-981D-F8A32C5B4E10")
    val L2CAP_PSM_CHARACTERISTIC_UUID = UUID.fromString("8C91321D-4A22-4215-99A1-3E2A15C81F4B") // Exposes dynamic L2CAP Port
    val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // Standard CCCD (Required for Notifications)

    var gattServer: BluetoothGattServer? = null
    var l2capServerSocket: android.bluetooth.BluetoothServerSocket? = null
    var myL2capPsm: Int = 0
    var l2capAcceptThread: Thread? = null
    
    
    val notificationHelper = NotificationHelper(context)
    var currentTeamKey: String = ""
    var isCloaked = false
    /** Direct radio links per phone. Larger meshes expand through routed neighbors. */
    val MAX_TOTAL_CONNECTIONS = 3 // MUST BE 3! If set to 1, it causes an infinite eviction loop.

    /** Hard limit imposed by the BLE `0xFFFF` manufacturer data field. */
    val MAX_ADVERT_PAYLOAD_BYTES = 26

    /**
     * How long an inbound server link may stay nameless before it is kicked. Raised from 5s because
     * a peer whose first inbound payload was a relayed message (non-empty routePath, so auto-rename
     * is intentionally skipped) could be dropped despite having a perfectly healthy socket.
     */
    val NAME_HANDSHAKE_TIMEOUT_MS = 10_000L

    /** How long to wait for the GATT link itself to come up. */
    val CONNECT_TIMEOUT_MS = 15_000L

    /**
     * Absolute ceiling on the post-connect handshake (discovery and descriptor write). Always
     * fires, so a dropped OEM callback can never strand the connect lock.
     */
    val HANDSHAKE_WATCHDOG_MS = 15_000L

    /** Bounded retry for a synchronous `discoverServices()` rejection from a busy OEM stack. */
    val DISCOVERY_REQUEST_RETRY_MS = 300L
    val MAX_DISCOVERY_REQUEST_ATTEMPTS = 3

    /** Hard ceiling on holding the connect lock, enforced by [timeoutRunnable]. */
    val CONNECT_LOCK_MAX_HOLD_MS = 25_000L

    /**
     * Window in which two links to the same peer are treated as a simultaneous-connect collision
     * rather than a redundant duplicate.
     */
    val DUPLICATE_LINK_GRACE_MS = 5_000L

    val payloadDispatcherCallback = object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getSeenMessageIds() = store.seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
        override fun getConnectedEndpointIdByName(name: String) =
            store.connectedEndpointNames.entries.find { NodeIdentity.matches(it.value, name) }?.key
        override fun getStpNeighbors(): Set<String> {
            return this@NativeBleManager.stpNeighborsProvider?.invoke() ?: emptySet()
        }
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun sendGattPayload(endpointId: String, payload: ByteArray) {
            this@NativeBleManager.enqueueGattPayload(endpointId, payload, priority = true)
        }
        override fun onHeartbeatAck(endpointId: String, challengeId: String) = this@NativeBleManager.onHeartbeatAck(endpointId, challengeId)
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) = this@NativeBleManager.broadcastPayload(payload, excludeEndpointId)
        override fun onMessageSeen(msgId: String, readerName: String) { onMessageSeen?.invoke(msgId, readerName) }
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
        override fun onPublicKeyReceived(endpointId: String, senderName: String, key: String) { onPublicKeyReceived?.invoke(endpointId, senderName, key) }
        override fun onRoutingTableReceived(senderName: String, connectedNodes: List<String>) { onRoutingTableReceived?.invoke(senderName, connectedNodes) }
        override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String) {
            this@NativeBleManager.onMessageReceived?.invoke(endpointId, msgId, senderName, text, isPrivate, isSystem, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, channelId)
        }
        override fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray) {
            this@NativeBleManager.onLiveAudioChunk?.invoke(sender, channelId, chunk)
        }
        override fun onDeviceNameSync(endpointId: String, realName: String) {
            // Deprecated: We now handle this safely in processBinaryPayload 
            // by enforcing routePath.isEmpty() to prevent relayed pulses from corrupting the routing table.
        }
        override fun onDeviceGoodbye(endpointId: String) {
            AppLogger.d("BLE_MESH", "Received GOODBYE packet from $endpointId. Disconnecting instantly.")
            this@NativeBleManager.disconnectFromEndpoint(endpointId)
        }
        override fun onSosCancelled() { this@NativeBleManager.onSosCancelled?.invoke() }
        override fun isDeviceBlocked(deviceName: String) = this@NativeBleManager.isDeviceBlocked(deviceName)
        override fun onDeviceBlocked(deviceName: String) { this@NativeBleManager.onDeviceBlocked?.invoke(deviceName) }
        override fun onDeviceUnblocked(deviceName: String) { this@NativeBleManager.onDeviceUnblocked?.invoke(deviceName) }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    }
    
    val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    val handler = Handler(Looper.getMainLooper())
    private val heartbeatChallenges = ConcurrentHashMap<String, HeartbeatChallenge>()
    private val HEARTBEAT_ACK_TIMEOUT_MS = 8_000L
    private val GATT_CHUNK_TIMEOUT_MS = 4_000L
    val timeoutRunnable = object : Runnable {
        override fun run() {
            if (!store.isNodeActive.get()) return
            val now = System.currentTimeMillis()

            // DEADLOCK BACKSTOP: if the connect lock has been held beyond any legitimate handshake
            // duration, a GATT callback was dropped. Force-release it, otherwise this device can
            // never initiate another outbound connection for the rest of the session.
            val lockAge = now - store.connectLockAcquiredAt
            if (store.isConnecting.get() && store.connectLockAcquiredAt > 0L && lockAge > CONNECT_LOCK_MAX_HOLD_MS) {
                AppLogger.d("BLE_MESH", "Connect lock stuck for ${lockAge}ms on ${store.connectingMacAddress}. Force-releasing.")
                releaseConnectLock(null, "stuck lock backstop", force = true)
            }

            // A peer process can restart while Android leaves its old GATT role alive. Check
            // actual active roles, including central addresses absent from the scan cache.
            val activeEndpoints = (store.activeConnections.keys + store.activeServerConnections.keys).toSet()
            activeEndpoints.forEach { macAddress ->
                val lastInbound = store.connectionInteractionTimes[macAddress]
                    ?: store.connectionEstablishTime[macAddress] ?: now
                onDeviceLivenessChanged?.invoke(macAddress, !BleLivenessPolicy.isUnresponsive(lastInbound, now))
                if (!store.activeL2capSockets.containsKey(macAddress) &&
                    now - lastInbound >= BleLivenessPolicy.UNRESPONSIVE_AFTER_MS &&
                    !heartbeatChallenges.containsKey(macAddress)) {
                    startHeartbeatChallenge(macAddress)
                }
                if (BleLivenessPolicy.isStale(lastInbound, now)) {
                    val probe = heartbeatChallenges[macAddress]
                    if (probe != null && now - probe.createdAt < 30_000L &&
                        (probe.sentAt == 0L || !probe.expired(now, HEARTBEAT_ACK_TIMEOUT_MS))) {
                        AppLogger.d("BLE_MESH", "Waiting for generation-bound GATT heartbeat result on $macAddress")
                        return@forEach
                    }
                    AppLogger.d("BLE_MESH", "No inbound progress from $macAddress for ${now - lastInbound}ms. Retiring stale GATT roles.")
                    heartbeatChallenges.remove(macAddress)
                    store.activeConnections[macAddress]?.let { forceGattDisconnect(macAddress, it) }
                    store.activeServerConnections[macAddress]?.let { gattServer?.cancelConnection(it) }
                } else {
                    store.endpointLastSeen[macAddress] = now
                }
            }

            val iterator = store.endpointLastSeen.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val macAddress = entry.key
                if (macAddress in activeEndpoints) continue
                if (now - entry.value > 8000) {
                    store.connectedEndpointIds.remove(macAddress)
                    store.connectedEndpointNames.remove(macAddress)
                    store.endpointLastSeen.remove(macAddress)
                    store.endpointNodeIds.remove(macAddress)
                    AppLogger.d("BLE_MESH", "Node Timed Out: ${macAddress}")
                    onDeviceDisconnected?.invoke(macAddress)
                    onDeviceScanRemoved?.invoke(macAddress)
                    sendSystemPulse()
                }
            }
            
            // HEARTBEAT FIX: Periodically send a SYSTEM pulse to keep connections alive and prevent
            // the other side's Watchdog from aggressively killing the connection as a Zombie Socket.
            sendSystemPulse()
            
            handler.postDelayed(this, 5000)
        }
    }

    companion object {
        var activeAdvertiseCallback: AdvertiseCallback? = null
        @SuppressLint("StaticFieldLeak")
        var instance: NativeBleManager? = null
    }


    fun startMeshNode(teamKey: String) {
        currentTeamKey = teamKey
        isCloaked = false
        if (bleAdvertiser == null || bleScanner == null) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        store.isNodeActive.set(true)
        activeAdvertiseCallback = advertiseCallback
        instance = this
        
        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        handler.post(timeoutRunnable)
        onStatusChanged?.invoke("Mesh Active [Persistent GATT/Protobuf]. Seeking peers...")
    }
    
    private var lastSystemPulseHash: Int = 0
    private var lastSystemPulseTime: Long = 0
    private var pingCounter: Int = 0

    fun sendSystemPulse(forceFull: Boolean = false) {
        if (!store.isNodeActive.get()) return
        try {
            val connectedNodesList = store.connectedEndpointNames.values
                .filter { !NodeIdentity.isPlaceholder(it) }
                .toList().sorted()
            val currentHash = connectedNodesList.hashCode()
            val now = System.currentTimeMillis()
            
            // DELTA PING FIX: If topology hasn't changed and it's been less than 60s, send a 10-byte MICRO-PING instead
            if (!forceFull && currentHash == lastSystemPulseHash && (now - lastSystemPulseTime < 60000)) {
                pingCounter++
                val payload = com.example.testresqmesh.core.network.MeshPayload(
                    id = "P$pingCounter",
                    type = "PING",
                    senderName = myDeviceName
                )
                val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
                broadcastPayload(payloadBytes)
                return
            }
            
            // Topology changed or 60s passed! Send full 141-byte SYSTEM sync.
            lastSystemPulseHash = currentHash
            lastSystemPulseTime = now
            
            val pulseId = java.util.UUID.randomUUID().toString()
            val payload = com.example.testresqmesh.core.network.MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName,
                connectedNodes = connectedNodesList,
                publicKey = com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64()
            )
            val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
            AppLogger.event(
                category = com.example.testresqmesh.core.utils.TerminalLogCategory.SYNC,
                event = "SYSTEM_SENT",
                message = "Sending full SYSTEM pulse to ${store.activeConnections.size + store.activeServerConnections.size} GATT endpoints",
                tag = "BLE_MESH"
            )
            broadcastPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }

    fun stopMeshNode() {
        store.isNodeActive.set(false)
        AppLogger.clearLinks()
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        if (scanActive.compareAndSet(true, false)) bleScanner?.stopScan(scanCallback)
        handshakeRadioGate.clear()
        
        try {
            val goodbyePayload = MeshPayload(
                id = java.util.UUID.randomUUID().toString(),
                type = "GOODBYE",
                senderName = myDeviceName
            )
            val bytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(goodbyePayload)
            broadcastPayload(bytes)
        } catch (e: Exception) {}
        store.links.clear()
        
        store.activeServerConnections.values.forEach { gattServer?.cancelConnection(it) }
        store.activeServerConnections.clear()
        gattServer?.close()
        
        try {
            l2capServerSocket?.close()
            l2capAcceptThread?.interrupt()
        } catch (e: Exception) {}
        
        store.activeConnections.values.forEach { it.disconnect(); it.close() }
        store.activeConnections.clear()
        store.activeL2capSockets.values.forEach { socket ->
            try { socket.close() } catch (e: Exception) {}
        }
        store.activeL2capSockets.clear()
        store.pendingQueues.clear()
        store.gattFlights.clear()
        heartbeatChallenges.clear()
        store.isWriting.clear()
        handler.removeCallbacks(timeoutRunnable)
        store.connectedEndpointIds.clear()
        store.connectedEndpointNames.clear()
        store.endpointLastSeen.clear()
        store.endpointFirstSeen.clear()
        store.endpointNodeIds.clear()
        instance = null
        onStatusChanged?.invoke("Offline")
    }

    fun getElectionScore(): String {
        // Master Election based on Device Specs (RAM + CPU Cores)
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val ramGb = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
        val cores = Runtime.getRuntime().availableProcessors()
        
        // Formula: RAM (GB) * 10 + Cores. (e.g. 8GB RAM + 8 Cores = 88). Capped at 999.
        var specScore = (ramGb * 10) + cores
        if (specScore > 999) specScore = 999
        if (specScore < 0) specScore = 0
        
        return String.format("%03d%s", specScore, myHex.take(2)) // e.g. "0889F", sorts by Specs, breaks ties with Hex
    }

    fun startAdvertising(teamKey: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // Advertise distinct peers, not raw socket count. Double-counting a peer linked on two MACs
        // made neighbours believe this node was at capacity and skewed their orphan-rescue logic.
        val totalConnections = distinctLinkCount()
        val electionScore = getElectionScore()

        // IDENTITY FIX: advertise the stable node ID as its own field. Previously the whole
        // "score|connections|fullName" string was blindly cut at 26 bytes, which amputated the
        // "[TAG]#NODE_ID" suffix on longer device names and left the mesh with no reliable way to
        // tell two similarly named peers apart.
        val nodeId = NodeIdentity.idOf(myDeviceName) ?: myNodeId.ifEmpty { myHex }
        val prefix = "$electionScore|$totalConnections|$nodeId|"
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
        val nameBudget = (MAX_ADVERT_PAYLOAD_BYTES - prefixBytes.size).coerceAtLeast(0)
        val displayName = truncateToBytes(NodeIdentity.displayNameOf(myDeviceName), nameBudget)

        var nameBytes = (prefix + displayName).toByteArray(Charsets.UTF_8)
        if (nameBytes.size > MAX_ADVERT_PAYLOAD_BYTES) {
            nameBytes = nameBytes.sliceArray(0 until MAX_ADVERT_PAYLOAD_BYTES)
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
            
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(0xFFFF, nameBytes)
            .build()
            
        bleAdvertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    /**
     * Truncates [value] so its UTF-8 encoding fits within [maxBytes], dropping whole characters so a
     * multi-byte codepoint is never cut in half (which would corrupt the advertisement).
     */
    private fun truncateToBytes(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        var end = value.length
        while (end > 0) {
            val candidate = value.substring(0, end)
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) return candidate
            end--
        }
        return ""
    }

    val advertiseCallback = object : AdvertiseCallback() {}

    fun startScanning() {
        val scanner = bleScanner ?: return
        if (!scanActive.compareAndSet(false, true)) return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            scanActive.set(false)
            AppLogger.d("BLE_MESH", "Scanner start failed: ${e.message}")
        }
    }

    fun beginRadioHandshake(owner: String) {
        if (!handshakeRadioGate.begin(owner)) return
        if (scanActive.compareAndSet(true, false)) {
            try { bleScanner?.stopScan(scanCallback) } catch (_: Exception) {}
        }
        AppLogger.d("BLE_MESH", "Radio handshake gate acquired by $owner; scanning paused")
    }

    fun isRadioHandshakeActive(): Boolean = handshakeRadioGate.isActive()

    fun finishRadioHandshake(owner: String, reason: String) {
        if (!handshakeRadioGate.finish(owner)) return
        AppLogger.d("BLE_MESH", "Radio handshake gate released by $owner ($reason); scanning resumed")
        if (store.isNodeActive.get()) startScanning()
    }

    fun latestEndpointForIdentity(peerName: String, fallback: String): String {
        val targetId = NodeIdentity.idOf(peerName)
        return store.connectedEndpointNames.entries
            .asSequence()
            .filter { entry ->
                val name = entry.value
                NodeIdentity.matches(name, peerName) ||
                    (targetId != null && store.endpointNodeIds[entry.key] == targetId)
            }
            .maxByOrNull { store.endpointLastSeen[it.key] ?: Long.MIN_VALUE }
            ?.key ?: fallback
    }

    val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            scanActive.set(false)
            AppLogger.d("BLE_MESH", "Scanner failed with errorCode=$errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0xFFFF)
            if (manufacturerData == null) return
            
            val serviceDataStr = String(manufacturerData, Charsets.UTF_8).replace("\u0000", "").trim()
            val parts = serviceDataStr.split("|")

            // STRICT FILTER: If the advertisement does not match the ResQMesh signature, ignore it.
            // Format is "score|connections|nodeId|displayName". Three-part advertisements are the
            // legacy "score|connections|fullName" layout and are still accepted so a partially
            // upgraded mesh keeps working.
            val peerScore: String
            val peerConnections: Int
            val advertisedName: String
            val advertisedNodeId: String
            when {
                parts.size >= 4 -> {
                    peerScore = parts[0]
                    peerConnections = parts[1].toIntOrNull() ?: -1
                    advertisedNodeId = parts[2].trim().uppercase()
                    advertisedName = parts.drop(3).joinToString("|")
                }
                parts.size == 3 -> {
                    peerScore = parts[0]
                    peerConnections = parts[1].toIntOrNull() ?: -1
                    advertisedName = parts[2]
                    advertisedNodeId = NodeIdentity.idOf(parts[2]).orEmpty()
                }
                else -> {
                    AppLogger.d("BLE_MESH", "Scanner: Ignored alien device ${device.address}. Invalid signature: $serviceDataStr")
                    return
                }
            }

            val peerName = if (advertisedNodeId.isNotEmpty()) {
                NodeIdentity.compose(advertisedName, advertisedNodeId)
            } else {
                advertisedName.trim()
            }
            if (peerName.isEmpty()) return
            val macAddress = device.address

            if (isDeviceBlocked(peerName)) {
                return
            }

            // GHOST NODE EVICTION & DUAL-MAC SPLIT-BRAIN FIX:
            // Android uses different MACs for scanning (Central) vs advertising (Peripheral).
            val oldMac = store.connectedEndpointNames.entries
                .find { NodeIdentity.matches(it.value, peerName) }?.key
            if (oldMac != null && oldMac != macAddress) {
                val isOldMacPhysicallyConnected = store.activeConnections.containsKey(oldMac) || store.activeServerConnections.containsKey(oldMac)
                
                if (isOldMacPhysicallyConnected) {
                    // We are physically connected to this peer on oldMac. The macAddress we just scanned 
                    // is simply their advertising MAC. If we evict it, we will destroy a perfectly healthy connection!
                    // Just ignore the advertisement to prevent a split-brain loop.
                    return
                } else {
                    // We are NOT physically connected. This is a true MAC rotation from a previously scanned device.
                    AppLogger.d("BLE_MESH", "GHOST EVICTION: $peerName rotated MAC from $oldMac to $macAddress. Purging ghost.")
                    store.connectedEndpointIds.remove(oldMac)
                    store.connectedEndpointNames.remove(oldMac)
                    store.endpointLastSeen.remove(oldMac)
                    store.endpointNodeIds.remove(oldMac)
                    handler.post {
                        onDeviceDisconnected?.invoke(oldMac)
                    }
                }
            }

            if (!NodeIdentity.matches(peerName, myDeviceName)) {
                val now = System.currentTimeMillis()
                store.endpointLastSeen[macAddress] = now
                store.endpointLastScore[macAddress] = peerScore
                if (advertisedNodeId.isNotEmpty()) {
                    store.endpointNodeIds[macAddress] = advertisedNodeId
                }
                if (!store.endpointFirstSeen.containsKey(macAddress)) {
                    store.endpointFirstSeen[macAddress] = now
                }

                if (!store.connectedEndpointIds.contains(macAddress)) {
                    store.connectedEndpointIds.add(macAddress)
                    store.connectedEndpointNames[macAddress] = peerName
                    
                    handler.post {
                        onDeviceScanned?.invoke(
                            com.example.testresqmesh.core.model.ScanEvent(
                                endpointId = macAddress,
                                name = peerName,
                                nodeId = advertisedNodeId,
                                peerConnections = peerConnections,
                                peerScore = peerScore,
                                isConnecting = false
                            )
                        )
                        sendSystemPulse()
                    }
                }

                val isAlreadyConnected = hasLinkToIdentity(peerName) ||
                    store.activeConnections.containsKey(macAddress) ||
                    store.activeServerConnections.containsKey(macAddress)
                val hasIndirectRoute = checkRouteExists?.invoke(peerName) == true

                if (!isAlreadyConnected && !hasIndirectRoute) {
                    val totalConnections = distinctLinkCount()
                    
                    if (totalConnections >= MAX_TOTAL_CONNECTIONS || (totalConnections >= 2 && peerConnections > 0)) {
                        if (peerConnections == 0 && totalConnections >= MAX_TOTAL_CONNECTIONS) {
                            if (!store.orphanDetectionTime.containsKey(macAddress)) {
                                store.orphanDetectionTime[macAddress] = now
                            } else {
                                if (now - (store.orphanDetectionTime[macAddress] ?: now) > 5000) {
                                    AppLogger.d("BLE_MESH", "Orphan Preemption: Found orphan $peerName. Dropping weakest link to rescue.")
                                    val lruMac = store.connectionInteractionTimes
                                        .filterKeys { store.activeConnections.containsKey(it) }
                                        .filterKeys { store.pendingQueues[it]?.isEmpty() != false } // QA FIX: Only evict idle connections
                                        .minByOrNull { it.value }?.key
                                        
                                    if (lruMac != null) {
                                        store.activeConnections[lruMac]?.disconnect()
                                        store.activeConnections[lruMac]?.close()
                                        store.activeConnections.remove(lruMac)
                                        store.pendingQueues.remove(lruMac)
                                        store.isWriting.remove(lruMac)
                                        store.chunkBuffers.remove(lruMac)
                                        store.connectionInteractionTimes.remove(lruMac)
                                        handler.post {
                                            onDeviceDisconnected?.invoke(lruMac)
                                        }
                                        connectToPersistentGatt(macAddress, peerName)
                                    }
                                }
                            }
                        } else {
                            store.orphanDetectionTime.remove(macAddress)
                        }
                    } else {
                        val lastAttempt = store.connectionAttempts[macAddress] ?: 0L
                        // WATCHDOG LIMIT: Enforce strict 15-second cool-down backoff timer to prevent GATT 133 Crash Loops!
                        if (now - lastAttempt > 15000) {
                            store.connectionAttempts[macAddress] = now
                            
                            val myScore = getElectionScore()
                            if (peerScore.isNotEmpty() && myScore > peerScore) {
                                AppLogger.d("BLE_MESH", "Battery Master Election: $myScore > $peerScore. Initiating connection with jitter.")
                                handler.postDelayed({
                                    val latestEndpoint = latestEndpointForIdentity(peerName, macAddress)
                                    if (latestEndpoint != macAddress) {
                                        AppLogger.d("BLE_MESH", "Resolved rotated endpoint for $peerName: $macAddress -> $latestEndpoint")
                                    }
                                    connectToPersistentGatt(latestEndpoint, peerName)
                                }, (100L..1000L).random())
                            } else if (peerScore.isEmpty()) {
                                if (myDeviceName > peerName) {
                                    AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: $myDeviceName > $peerName. Initiating connection.")
                                    connectToPersistentGatt(macAddress, peerName)
                                }
                            } else {
                                AppLogger.d("BLE_MESH", "Battery Master Election: $myScore <= $peerScore. Yielding.")
                            }
                        }
                    }
                }
            }
        }
    }

    fun connectToPersistentGatt(macAddress: String, peerName: String) { gattClientManager.connectToPersistentGatt(macAddress, peerName) }

    // ---------------------------------------------------------------------------------------------
    // Connect lock
    // ---------------------------------------------------------------------------------------------

    /**
     * Acquires the single-flight outbound connection lock.
     *
     * Android's GATT stack misbehaves badly with concurrent `connectGatt` calls, so outbound
     * connections are deliberately serialised. The lock records its acquisition time so
     * [timeoutRunnable] can force-release it if a GATT callback is dropped by the OEM stack.
     */
    fun tryAcquireConnectLock(macAddress: String): Boolean {
        if (!store.isConnecting.compareAndSet(false, true)) return false
        store.connectingMacAddress = macAddress
        store.connectLockAcquiredAt = System.currentTimeMillis()
        return true
    }

    /**
     * Releases the connect lock if it is held for [macAddress] (or unconditionally when
     * [force] is set).
     *
     * Every terminal path must funnel through here. Previously the lock was cleared inline in
     * five different GATT callbacks plus a timeout, and the success path relied on
     * `onMtuChanged -> discoverServices -> onServicesDiscovered` completing. If any OEM dropped
     * one of those callbacks the lock stayed held forever and the device could never initiate
     * another outbound connection for the rest of the session.
     */
    fun releaseConnectLock(macAddress: String?, reason: String, force: Boolean = false) {
        val holder = store.connectingMacAddress
        if (!force && macAddress != null && holder != null && holder != macAddress) return
        store.connectingMacAddress = null
        store.connectLockAcquiredAt = 0L
        if (store.isConnecting.compareAndSet(true, false)) {
            AppLogger.d("BLE_MESH", "Connect lock released for ${macAddress ?: "*"} ($reason)")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Identity-aware link queries
    // ---------------------------------------------------------------------------------------------

    /** True when a live client or server socket exists on this exact endpoint. */
    fun hasLiveSocket(endpointId: String): Boolean =
        store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)

    fun hasReadyEndpoint(endpointId: String): Boolean =
        hasLiveSocket(endpointId) && store.links.isReady(endpointId)

    /** Retire the data socket and compatibility state only after the last GATT role ends. */
    fun cleanupEndpointIfUnowned(endpointId: String) {
        if (hasLiveSocket(endpointId)) return
        store.activeL2capSockets.remove(endpointId)?.let { socket ->
            try { socket.close() } catch (e: Exception) {}
        }
        store.pendingQueues.remove(endpointId)
        store.gattFlights.remove(endpointId)?.writing?.set(false)
        heartbeatChallenges.remove(endpointId)
        store.isWriting.remove(endpointId)
        store.chunkBuffers.remove(endpointId)
        store.connectionMtu.remove(endpointId)
        store.writeFailureCount.remove(endpointId)
        store.connectionInteractionTimes.remove(endpointId)
        AppLogger.d("BLE_MESH", "Retired unowned endpoint transport $endpointId")
    }

    fun hasReadyLinkToIdentity(peerName: String): Boolean {
        val targetId = NodeIdentity.idOf(peerName)
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        return endpoints.any { endpoint ->
            store.links.isReady(endpoint) &&
                (NodeIdentity.matches(store.connectedEndpointNames[endpoint], peerName) ||
                    (targetId != null && nodeIdForEndpoint(endpoint) == targetId))
        }
    }

    /** When this link was established, or [Long.MAX_VALUE] when unknown. */
    fun linkEstablishedAt(endpointId: String): Long =
        store.connectionEstablishTime[endpointId] ?: Long.MAX_VALUE

    /** The stable node ID associated with an endpoint, from its name or its advertisement. */
    fun nodeIdForEndpoint(endpointId: String): String? =
        NodeIdentity.idOf(store.connectedEndpointNames[endpointId]) ?: store.endpointNodeIds[endpointId]

    /**
     * Finds an existing live socket belonging to the same node as [peerName], regardless of which
     * MAC it was established on.
     *
     * Android advertises on a different MAC than it connects with, so every MAC-keyed duplicate
     * check was silently defeated: a peer already connected inbound on its Central MAC looked
     * absent when matched against its advertising MAC, and we would dial a second redundant link
     * to the same device.
     */
    fun findLinkEndpointByIdentity(peerName: String): String? {
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        endpoints.firstOrNull { NodeIdentity.matches(store.connectedEndpointNames[it], peerName) }
            ?.let { return it }

        // Fall back to the advertised node ID. This also covers provisional sockets whose name is
        // still a placeholder but whose ID was seeded from a previous scan.
        val targetId = NodeIdentity.idOf(peerName)
        if (targetId != null) {
            endpoints.firstOrNull { nodeIdForEndpoint(it) == targetId }?.let { return it }
        }
        return null
    }

    fun hasLinkToIdentity(peerName: String): Boolean = findLinkEndpointByIdentity(peerName) != null

    /**
     * Number of connected *peers*, not sockets.
     *
     * `activeConnections.size + activeServerConnections.size` double-counted a peer that was
     * linked on two MACs, so a node could believe it was at capacity while actually holding a
     * single duplicated link.
     */
    fun distinctLinkCount(): Int {
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        return endpoints
            .map { endpoint ->
                nodeIdForEndpoint(endpoint)
                    ?: NodeIdentity.key(store.connectedEndpointNames[endpoint]).ifEmpty { endpoint }
            }
            .toSet()
            .size
    }


    fun processNextPayload(macAddress: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { processNextPayload(macAddress) }
            return
        }
        if (hasUsableL2cap(macAddress)) {
            promoteGattWorkToL2cap(macAddress)
            return
        }
        val link = readyGattLink(macAddress)
        if (link == null) return
        val queue = store.pendingQueues[macAddress] ?: return
        val writing = store.isWriting[macAddress] ?: return
        store.gattFlights[macAddress]?.takeIf { !store.links.isCurrent(it.link) }?.let { stale ->
            if (store.gattFlights.remove(macAddress, stale)) stale.writing.set(false)
        }
        if (!writing.compareAndSet(false, true)) return
        val transfer = queue.pollFirst()
        if (transfer == null) {
            writing.set(false)
            return
        }
        val flight = GattTransferFlight(
            transfer, link, queue, writing,
            if (link.role == BleLinkRole.CLIENT) store.activeConnections[macAddress] else null,
            if (link.role == BleLinkRole.SERVER) store.activeServerConnections[macAddress] else null
        )
        store.gattFlights[macAddress] = flight
        sendGattChunk(flight)
    }

    private fun sendGattChunk(flight: GattTransferFlight) {
        val endpoint = flight.link.endpoint
        if (!store.links.isCurrent(flight.link) || flight.link.state != BleLinkState.READY ||
            store.pendingQueues[endpoint] !== flight.queue ||
            store.gattFlights[endpoint] !== flight) return
        val remaining = flight.transfer.frame.size - flight.offset
        if (remaining <= 0) return
        val mtu = (store.connectionMtu[endpoint] ?: 20).coerceAtLeast(1)
        val chunk = flight.transfer.frame.copyOfRange(flight.offset, flight.offset + minOf(mtu, remaining))
        flight.chunkLength = chunk.size
        val operationId = ++flight.operationId
        val initiated = if (flight.link.role == BleLinkRole.CLIENT) {
            val gatt = flight.gatt
            val characteristic = gatt?.getService(SERVICE_UUID)?.getCharacteristic(RX_CHARACTERISTIC_UUID)
            if (gatt == null || characteristic == null || store.activeConnections[endpoint] !== gatt) false
            else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                gatt.writeCharacteristic(characteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = chunk
                gatt.writeCharacteristic(characteristic)
            }
        } else {
            val device = flight.serverDevice
            val tx = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(TX_CHARACTERISTIC_UUID)
            if (device == null || tx == null || store.activeServerConnections[endpoint] !== device) false
            else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                gattServer?.notifyCharacteristicChanged(device, tx, true, chunk) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            else {
                tx.value = chunk
                gattServer?.notifyCharacteristicChanged(device, tx, true) == true
            }
        }
        if (!initiated) {
            if (++flight.retryCount >= 5) {
                failGattFlight(flight, "GATT initiation rejected 5 times")
            } else {
                handler.postDelayed({
                    if (store.gattFlights[endpoint] === flight && flight.operationId == operationId)
                        sendGattChunk(flight)
                }, 200)
            }
            return
        }
        handler.postDelayed({
            if (store.gattFlights[endpoint] === flight && flight.operationId == operationId)
                failGattFlight(flight, "GATT completion callback timed out")
        }, GATT_CHUNK_TIMEOUT_MS)
    }

    fun completeGattChunk(
        endpoint: String,
        role: BleLinkRole,
        status: Int,
        gatt: BluetoothGatt? = null,
        device: BluetoothDevice? = null
    ) {
        handler.post {
            val flight = store.gattFlights[endpoint] ?: return@post
            if (flight.link.role != role || !store.links.isCurrent(flight.link) ||
                (role == BleLinkRole.CLIENT && flight.gatt !== gatt) ||
                (role == BleLinkRole.SERVER && flight.serverDevice !== device)) return@post
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGattFlight(flight, "GATT completion failed status=$status")
                return@post
            }
            flight.offset += flight.chunkLength
            flight.chunkLength = 0
            flight.retryCount = 0
            if (flight.offset < flight.transfer.frame.size) {
                sendGattChunk(flight)
            } else if (store.gattFlights.remove(endpoint, flight)) {
                flight.writing.set(false)
                flight.transfer.heartbeatId?.let { markHeartbeatSent(endpoint, flight.link, it) }
                processNextPayload(endpoint)
            }
        }
    }

    private fun failGattFlight(flight: GattTransferFlight, reason: String) {
        val endpoint = flight.link.endpoint
        if (!store.gattFlights.remove(endpoint, flight)) return
        flight.writing.set(false)
        heartbeatChallenges.remove(endpoint)
        if (hasUsableL2cap(endpoint)) {
            AppLogger.d("BLE_MESH", "Link ${flight.link.generation} $endpoint: $reason; preserving healthy L2CAP and promoting payload")
            sendDirectPayload(endpoint, flight.transfer.payloadBytes())
            promoteGattWorkToL2cap(endpoint)
            return
        }
        AppLogger.d("BLE_MESH", "Link ${flight.link.generation} $endpoint: $reason; retiring link")
        if (!store.links.isCurrent(flight.link)) {
            processNextPayload(endpoint)
            return
        }
        if (flight.link.role == BleLinkRole.CLIENT)
            flight.gatt?.let { forceGattDisconnect(endpoint, it) }
        else
            flight.serverDevice?.let { gattServer?.cancelConnection(it) }
    }
    fun startGattServer() { gattServerManager.startGattServer() }

    fun startL2capServer() { gattServerManager.startL2capServer() }

    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket) {
        store.activeL2capSockets.put(macAddress, socket)?.let { old ->
            if (old !== socket) try { old.close() } catch (e: Exception) {}
        }
        AppLogger.updateLinkTransport(macAddress, "GATT+L2CAP")
        // GATT becomes READY before the L2CAP socket on many phones. Any payload started in that
        // small window must no longer be allowed to time out and tear down the now-healthy L2CAP
        // transport. Promote both the active transfer and queued transfers on the main thread.
        handler.post { promoteGattWorkToL2cap(macAddress) }
        // A pulse queued before the app transport was ready may have become a keyless PING.
        // Every new L2CAP socket needs a fresh, key-bearing pulse in both directions.
        sendSystemPulse(forceFull = true)
        Thread {
            try {
                val din = java.io.DataInputStream(socket.inputStream)
                while (store.isNodeActive.get() && socket.isConnected &&
                    store.activeL2capSockets[macAddress] === socket && hasLiveSocket(macAddress)) {
                    val length = din.readInt()
                    if (length > 0 && length < 10 * 1024 * 1024) { // Max 10MB sanity check
                        val payloadBytes = ByteArray(length)
                        din.readFully(payloadBytes)
                        store.connectionInteractionTimes[macAddress] = System.currentTimeMillis()
                        AppLogger.d("BLE_MESH", "L2CAP Received ${length} bytes from $macAddress")
                        if (store.activeL2capSockets[macAddress] === socket && hasLiveSocket(macAddress)) {
                            processBinaryPayload(macAddress, payloadBytes)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.d("BLE_MESH", "L2CAP stream disconnected for $macAddress: ${e.message}")
            } finally {
                val removed = store.activeL2capSockets.remove(macAddress, socket)
                try { socket.close() } catch (e: Exception) {}
                if (removed) handler.post { startHeartbeatChallenge(macAddress) }
            }
        }.start()
    }

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        if (!hasLiveSocket(endpointId)) {
            AppLogger.d("BLE_MESH", "Dropping payload from unowned endpoint $endpointId")
            return
        }
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            onDeviceLivenessChanged?.invoke(endpointId, true)
            if (payload.type != "PONG") heartbeatChallenges.remove(endpointId)
            
            // Only auto-rename the physical socket if this is a direct message (not relayed).
            // Relayed payloads carry a non-empty routePath; renaming from those would map a remote
            // node onto a local socket and corrupt the routing table.
            if (payload.routePath.isEmpty() && payload.senderName.isNotEmpty()) {
                val oldName = store.connectedEndpointNames[endpointId]
                if (NodeIdentity.isPlaceholder(oldName) || !NodeIdentity.matches(oldName, payload.senderName)) {
                    AppLogger.d("BLE_MESH", "Auto-rename: $endpointId is now ${payload.senderName}")
                    store.connectedEndpointNames[endpointId] = payload.senderName
                    NodeIdentity.idOf(payload.senderName)?.let { nodeId ->
                        store.endpointNodeIds[endpointId] = nodeId
                        listOf(
                            BleLinkRole.CLIENT,
                            BleLinkRole.SERVER
                        ).forEach { role ->
                            store.links.current(endpointId, role)?.peerNodeId = nodeId
                        }
                    }
                    handler.post {
                        if (!hasLiveSocket(endpointId)) return@post
                        val isDirectlyConnected = store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)
                        onDeviceConnected?.invoke(
                            com.example.testresqmesh.core.model.ConnectedDevice(
                                endpointId = endpointId,
                                name = payload.senderName,
                                isClassicConnected = isDirectlyConnected,
                                isProvisional = false,
                                nodeId = NodeIdentity.idOf(payload.senderName) ?: store.endpointNodeIds[endpointId].orEmpty(),
                                isPayloadReady = store.links.isReady(endpointId)
                            )
                        )
                        sendSystemPulse()
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to decode payload for auto-rename: ${e.message}")
        }
        
        payloadDispatcher.dispatch(endpointId, payloadBytes)
    }

    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null) {
        cacheOutgoingMessageId(payloadBytes)
        val targets = mutableSetOf<String>()
        targets.addAll(store.activeConnections.keys)
        targets.addAll(store.activeServerConnections.keys)
        targets.remove(excludeEndpointId)
        
        targets.forEach { targetId ->
            sendDirectPayload(targetId, payloadBytes)
        }
    }

    /** Queue one complete framed payload, bypassing L2CAP for a GATT health check. */
    private fun enqueueGattPayload(
        endpoint: String,
        payloadBytes: ByteArray,
        priority: Boolean = false,
        heartbeatId: String? = null
    ): Boolean {
        if (!hasReadyEndpoint(endpoint)) return false
        val frame = ByteBuffer.allocate(4 + payloadBytes.size)
            .putInt(payloadBytes.size).put(payloadBytes).array()
        val queue = store.pendingQueues.computeIfAbsent(endpoint) { ConcurrentLinkedDeque() }
        val transfer = GattTransfer(frame, heartbeatId)
        if (priority) queue.addFirst(transfer) else queue.addLast(transfer)
        store.isWriting.putIfAbsent(endpoint, AtomicBoolean(false))
        processNextPayload(endpoint)
        return true
    }

    private fun readyGattLink(endpoint: String) =
        store.links.current(endpoint, BleLinkRole.CLIENT)?.takeIf {
            it.state == BleLinkState.READY && store.activeConnections.containsKey(endpoint)
        } ?: store.links.current(endpoint, BleLinkRole.SERVER)?.takeIf {
            it.state == BleLinkState.READY && store.activeServerConnections.containsKey(endpoint)
        }

    private fun startHeartbeatChallenge(endpoint: String) {
        if (!store.isNodeActive.get() || heartbeatChallenges.containsKey(endpoint)) return
        val link = readyGattLink(endpoint) ?: return
        val id = "HB:${UUID.randomUUID()}"
        val challenge = HeartbeatChallenge(endpoint, link.role, link.generation, id, System.currentTimeMillis())
        heartbeatChallenges[endpoint] = challenge
        val payload = MeshPayload(id = id, type = "PING", senderName = myDeviceName)
        if (!enqueueGattPayload(endpoint, ProtoBuf.encodeToByteArray(payload), priority = true, heartbeatId = id)) {
            heartbeatChallenges.remove(endpoint, challenge)
        } else {
            AppLogger.d("BLE_MESH", "Link ${link.generation} $endpoint: queued GATT heartbeat challenge")
        }
    }

    private fun markHeartbeatSent(endpoint: String, link: com.example.testresqmesh.core.network.bluetooth.state.BleLink, id: String) {
        val pending = heartbeatChallenges[endpoint] ?: return
        if (pending.id != id || pending.generation != link.generation || !store.links.isCurrent(link)) return
        val sent = pending.copy(sentAt = System.currentTimeMillis())
        if (!heartbeatChallenges.replace(endpoint, pending, sent)) return
        handler.postDelayed({
            if (heartbeatChallenges[endpoint] != sent) return@postDelayed
            val current = store.links.current(endpoint, sent.role)
            if (current == null || current.generation != sent.generation) {
                heartbeatChallenges.remove(endpoint, sent)
                return@postDelayed
            }
            if (store.connectionInteractionTimes[endpoint]?.let { it > sent.sentAt } == true) {
                heartbeatChallenges.remove(endpoint, sent)
                return@postDelayed
            }
            if (store.activeL2capSockets.containsKey(endpoint)) {
                heartbeatChallenges.remove(endpoint, sent)
                return@postDelayed
            }
            if (sent.expired(System.currentTimeMillis(), HEARTBEAT_ACK_TIMEOUT_MS)) {
                heartbeatChallenges.remove(endpoint, sent)
                AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: GATT heartbeat ACK timed out; retiring silent roles")
                store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) }
                store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) }
            }
        }, HEARTBEAT_ACK_TIMEOUT_MS)
    }

    private fun onHeartbeatAck(endpoint: String, id: String) {
        handler.post {
            val pending = heartbeatChallenges[endpoint] ?: return@post
            val current = store.links.current(endpoint, pending.role) ?: return@post
            if (pending.accepts(endpoint, id, current.generation) && store.links.isCurrent(current)) {
                heartbeatChallenges.remove(endpoint, pending)
                AppLogger.d("BLE_MESH", "Link ${current.generation} $endpoint: GATT heartbeat acknowledged")
            }
        }
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray) {
        if (!BluetoothAdapter.checkBluetoothAddress(targetMacAddress)) return
        
        cacheOutgoingMessageId(payloadBytes)
        
        val fullData = ByteArray(4 + payloadBytes.size)
        val lengthBuffer = ByteBuffer.allocate(4).putInt(payloadBytes.size).array()
        System.arraycopy(lengthBuffer, 0, fullData, 0, 4)
        System.arraycopy(payloadBytes, 0, fullData, 4, payloadBytes.size)

        // PHASE 2 L2CAP ROUTING: Bypass GATT entirely if high-speed socket is available
        val l2capSocket = store.activeL2capSockets[targetMacAddress]
        if (l2capSocket != null && l2capSocket.isConnected && hasLiveSocket(targetMacAddress)) {
            Thread {
                try {
                    synchronized(l2capSocket) {
                        val dout = java.io.DataOutputStream(l2capSocket.outputStream)
                        dout.writeInt(payloadBytes.size)
                        dout.write(payloadBytes)
                        dout.flush()
                    }
                    AppLogger.d("BLE_MESH", "L2CAP Sent ${payloadBytes.size} bytes directly to $targetMacAddress")
                } catch (e: Exception) {
                    AppLogger.d("BLE_MESH", "L2CAP write failed to $targetMacAddress: ${e.message}")
                    if (store.activeL2capSockets.remove(targetMacAddress, l2capSocket)) {
                        try { l2capSocket.close() } catch (_: Exception) {}
                        enqueueGattPayload(targetMacAddress, payloadBytes)
                        handler.post { startHeartbeatChallenge(targetMacAddress) }
                    }
                }
            }.start()
            return
        }
        
        val isServerConnected = store.activeServerConnections.containsKey(targetMacAddress)
        val isClientConnected = store.activeConnections.containsKey(targetMacAddress)

        val queue = store.pendingQueues.computeIfAbsent(targetMacAddress) { ConcurrentLinkedDeque() }
        queue.addLast(GattTransfer(fullData))
        
        store.isWriting.putIfAbsent(targetMacAddress, AtomicBoolean(false))

        if (isServerConnected || isClientConnected) {
            processNextPayload(targetMacAddress)
        } else {
            if (distinctLinkCount() >= MAX_TOTAL_CONNECTIONS) {
                // VIP BOUNCER (LRU EVICTION)
                val lruMac = store.connectionInteractionTimes
                    .filterKeys { store.activeConnections.containsKey(it) }
                    .filterKeys { store.pendingQueues[it]?.isEmpty() != false } // QA FIX
                    .minByOrNull { it.value }?.key
                    
                val macToEvict = lruMac ?: store.activeConnections.keys.firstOrNull { store.pendingQueues[it]?.isEmpty() != false }
                
                if (macToEvict != null) {
                    AppLogger.d("BLE_MESH", "Evicting $macToEvict to make room for VIP connection to $targetMacAddress")
                    store.activeConnections[macToEvict]?.disconnect()
                    store.activeConnections[macToEvict]?.close()
                    store.activeConnections.remove(macToEvict)
                    store.pendingQueues.remove(macToEvict)
                    store.isWriting.remove(macToEvict)
                    store.chunkBuffers.remove(macToEvict)
                    store.connectionInteractionTimes.remove(macToEvict)
                    handler.post {
                        onDeviceDisconnected?.invoke(macToEvict)
                    }
                } else {
                    AppLogger.d("BLE_MESH", "VIP Bouncer failed: Cannot evict any connections because all are actively transmitting.")
                    return // Abort connecting to the new node to protect current data streams
                }
            }
            connectToPersistentGatt(targetMacAddress, store.connectedEndpointNames[targetMacAddress] ?: "Unknown")
        }
    }

    fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        try {
            val payload = ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes)
            if (payload.id.isNotEmpty()) store.seenMessageIds.add(payload.id)
        } catch (e: Exception) {}
    }

    fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?) {
        val link = store.links.current(macAddress, BleLinkRole.CLIENT)
        if (gatt != null && link?.gatt != null && link.gatt !== gatt) {
            AppLogger.d("BLE_MESH", "Ignoring forced cleanup from stale CLIENT GATT on $macAddress")
            try { gatt.close() } catch (e: Exception) {}
            return
        }
        if (link != null) {
            store.links.transition(link, BleLinkState.DISCONNECTING)
            store.links.forget(link)
        }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) { }
        
        if (gatt != null) store.activeConnections.remove(macAddress, gatt)
        else store.activeConnections.remove(macAddress)
        cleanupEndpointIfUnowned(macAddress)
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectedEndpointIds.remove(macAddress)
            store.connectedEndpointNames.remove(macAddress)
            store.endpointNodeIds.remove(macAddress)
        }
        if (!store.activeServerConnections.containsKey(macAddress)) {
            store.connectionEstablishTime.remove(macAddress)
        }
        releaseConnectLock(macAddress, "force disconnect")
        
        handler.post {
            onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
    }

    private fun hasUsableL2cap(endpoint: String): Boolean =
        store.activeL2capSockets[endpoint]?.isConnected == true && hasLiveSocket(endpoint)

    /**
     * Atomically hands pending GATT work to an established L2CAP socket.
     *
     * The in-flight transfer is resent in full. Payload IDs are deduplicated by the receiver, while
     * an incomplete GATT frame remains isolated in the GATT receive buffer and expires normally.
     */
    private fun promoteGattWorkToL2cap(endpoint: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { promoteGattWorkToL2cap(endpoint) }
            return
        }
        if (!hasUsableL2cap(endpoint)) return

        val promoted = mutableListOf<GattTransfer>()
        store.gattFlights.remove(endpoint)?.let { flight ->
            flight.writing.set(false)
            flight.transfer.heartbeatId?.let { heartbeatChallenges.remove(endpoint) }
            promoted += flight.transfer
        }
        store.pendingQueues[endpoint]?.let { queue ->
            while (true) promoted += queue.pollFirst() ?: break
        }
        store.isWriting[endpoint]?.set(false)
        if (promoted.isEmpty()) return

        AppLogger.d("BLE_MESH", "Promoting ${promoted.size} queued GATT transfer(s) to L2CAP for $endpoint")
        promoted.forEach { transfer -> sendDirectPayload(endpoint, transfer.payloadBytes()) }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        listOf(
            BleLinkRole.CLIENT,
            BleLinkRole.SERVER
        ).forEach { role ->
            store.links.current(endpointId, role)?.let { link ->
                store.links.transition(link, BleLinkState.DISCONNECTING)
                store.links.forget(link)
            }
        }
        val gatt = store.activeConnections.remove(endpointId)
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {}

        val serverDevice = store.activeServerConnections.remove(endpointId)
        serverDevice?.let { device ->
            try {
                gattServer?.cancelConnection(device)
            } catch (e: Exception) {}
        }

        val l2cap = store.activeL2capSockets.remove(endpointId)
        try {
            l2cap?.close()
        } catch (e: Exception) {}

        store.connectionEstablishTime.remove(endpointId)
        store.pendingQueues.remove(endpointId)
        store.gattFlights.remove(endpointId)?.writing?.set(false)
        heartbeatChallenges.remove(endpointId)
        store.isWriting.remove(endpointId)
        store.chunkBuffers.remove(endpointId)
        store.connectionMtu.remove(endpointId)
        store.connectionInteractionTimes.remove(endpointId)
        store.writeFailureCount.remove(endpointId)
        store.connectedEndpointIds.remove(endpointId)
        store.connectedEndpointNames.remove(endpointId)
        store.endpointNodeIds.remove(endpointId)
        releaseConnectLock(endpointId, "endpoint disconnected")
        if (gatt != null || serverDevice != null) {
            handler.post {
                onDeviceDisconnected?.invoke(endpointId)
                sendSystemPulse()
            }
        }
    }
    
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {
        store.blockedDevices[NodeIdentity.key(deviceName)] = true
        // Find and disconnect if currently connected
        val macAddress = store.connectedEndpointNames.entries
            .find { NodeIdentity.matches(it.value, deviceName) }?.key
        if (macAddress != null) {
            disconnectFromEndpoint(macAddress)
        }
    }

    /**
     * Blocked devices are keyed by [NodeIdentity.key] rather than the raw display name. The UI blocks
     * using the fully qualified name while the scanner only ever sees a truncated advertisement, so
     * the previous raw-name map lookup never matched and blocked peers kept reappearing in scans.
     */
    fun isDeviceBlocked(deviceName: String): Boolean {
        if (store.blockedDevices[NodeIdentity.key(deviceName)] == true) return true
        return store.blockedDevices.keys.any { key ->
            store.blockedDevices[key] == true && NodeIdentity.matches(key, deviceName)
        }
    }
    
    fun unblockDevice(deviceName: String) {
        store.blockedDevices.remove(NodeIdentity.key(deviceName))
        store.blockedDevices.keys
            .filter { NodeIdentity.matches(it, deviceName) }
            .forEach { store.blockedDevices.remove(it) }
    }
    
    fun rescan() {
        bleScanner?.stopScan(scanCallback)
        startScanning()
    }
    
    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        if (!store.activeConnections.containsKey(endpointId)) {
            connectToPersistentGatt(endpointId, endpointName)
        }
    }

    fun broadcastSeenReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null) {
        val payload = MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "SEEN",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }

    fun broadcastDeliveredReceipt(targetMessageId: String, isPrivate: Boolean, targetId: String? = null, directedReturnRoute: List<String> = emptyList()) {
        val payload = MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "DELIVERED",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate,
            returnRoute = listOf(myDeviceName),
            directedRoute = directedReturnRoute
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }
}


