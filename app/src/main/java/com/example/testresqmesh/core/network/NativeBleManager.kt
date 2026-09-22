package com.example.testresqmesh.core.network

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.BleAdvertisement
import com.example.testresqmesh.core.network.bluetooth.BleLifecycleSupervisor
import com.example.testresqmesh.core.network.bluetooth.BlePeerAdmissionController
import com.example.testresqmesh.core.network.bluetooth.BleRadioController
import com.example.testresqmesh.core.network.bluetooth.GattTransferExecutor
import com.example.testresqmesh.core.network.bluetooth.L2capTransport
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkRole
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.network.bluetooth.state.GattTransfer
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.NotificationHelper
import java.util.UUID
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
    var onRoutingTableReceived: ((String, String, List<String>, List<String>, Long) -> Unit)? = null
    var onSosCancelled: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null
    var onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    var onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    var checkRouteExists: ((String) -> Boolean)? = null

    var myDeviceName: String = "ResQMesh_Node"
    val myHex = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()

    /**
     * Stable, persisted node ID (SharedPreferences `node_id`) supplied by [MeshRepository.startNode].
     * Advertised as its own field so peer identity survives display-name truncation.
     */
    var myNodeId: String = ""
    fun getMeshProfileTtl(): Int {
        val isLongRange = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .getBoolean("mesh_profile_long_range", false)
        return if (isLongRange) 10 else 4
    }

    /** Shared platform handles retained for the GATT client/server managers. */
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    val SERVICE_UUID = UUID.fromString("B9A34F5C-7462-4C61-8935-7C2D4A15A3E4") // ResQMesh Custom Service
    val RX_CHARACTERISTIC_UUID = UUID.fromString("6A81C2E5-309F-4D88-B270-4A9A65D8B6C7")
    val TX_CHARACTERISTIC_UUID = UUID.fromString("1E4D9C7B-6F2A-4B9E-981D-F8A32C5B4E10")
    val L2CAP_PSM_CHARACTERISTIC_UUID = UUID.fromString("8C91321D-4A22-4215-99A1-3E2A15C81F4B") // Exposes dynamic L2CAP Port
    val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // Standard CCCD (Required for Notifications)
    private val radioController = BleRadioController(
        context = context,
        serviceUuid = SERVICE_UUID,
        isNodeActive = { store.isNodeActive.get() },
        onAdvertisement = ::handleAdvertisement
    )

    var gattServer: BluetoothGattServer? = null
    var l2capServerSocket: android.bluetooth.BluetoothServerSocket? = null
    var myL2capPsm: Int = 0
    var l2capAcceptThread: Thread? = null
    
    
    val notificationHelper = NotificationHelper(context)
    var currentTeamKey: String = ""
    var isCloaked = false
    /** Direct radio links per phone. Larger meshes expand through routed neighbors. */
    val MAX_TOTAL_CONNECTIONS = 3 // MUST BE 3! If set to 1, it causes an infinite eviction loop.

    /**
     * How long an inbound server link may stay nameless before it is kicked. Raised from 5s because
     * a peer whose first inbound payload was a relayed message (non-empty routePath, so auto-rename
     * is intentionally skipped) could be dropped despite having a perfectly healthy socket.
     */
    val NAME_HANDSHAKE_TIMEOUT_MS = 10_000L

    /** How long to wait for the GATT link itself to come up. */
    val CONNECT_TIMEOUT_MS = 5_000L

    /**
     * Absolute ceiling on the post-connect handshake (discovery and descriptor write). Always
     * fires, so a dropped OEM callback can never strand the connect lock.
     */
    val HANDSHAKE_WATCHDOG_MS = 5_000L

    /** Bounded retry for a synchronous `discoverServices()` rejection from a busy OEM stack. */
    val DISCOVERY_REQUEST_RETRY_MS = 300L
    val MAX_DISCOVERY_REQUEST_ATTEMPTS = 3

    /** Hard ceiling on holding the connect lock, enforced by [BleLifecycleSupervisor]. */
    val CONNECT_LOCK_MAX_HOLD_MS = 25_000L

    /**
     * Window in which two links to the same peer are treated as a simultaneous-connect collision
     * rather than a redundant duplicate.
     */
    val DUPLICATE_LINK_GRACE_MS = 5_000L

    val payloadDispatcherCallback = object : PayloadDispatcherCallback {
        override fun getMyDeviceName() = myDeviceName
        override fun getMyNodeId() = myNodeId
        override fun getSeenMessageIds() = store.seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
        override fun getConnectedEndpointIdByName(name: String) =
            store.connectedEndpointNames.entries.find { NodeIdentity.matches(it.value, name) }?.key
        override fun getConnectedEndpointIdByNodeId(nodeId: String) =
            (store.activeConnections.keys + store.activeServerConnections.keys).firstOrNull {
                val epNodeId = nodeIdForEndpoint(it) ?: return@firstOrNull false
                val idMatches = epNodeId.equals(nodeId, ignoreCase = true)
                idMatches && store.links.isReady(it)
            }
        override fun getStpNeighbors(): Set<String> {
            return this@NativeBleManager.stpNeighborsProvider?.invoke() ?: emptySet()
        }
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun sendPriorityPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendPriorityPayload(endpointId, payload)
        override fun sendGattPayload(endpointId: String, payload: ByteArray) {
            this@NativeBleManager.enqueueGattPayload(endpointId, payload, priority = true)
        }
        override fun onHeartbeatAck(endpointId: String, challengeId: String) = this@NativeBleManager.onHeartbeatAck(endpointId, challengeId)
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) = this@NativeBleManager.broadcastPayload(payload, excludeEndpointId)
        override fun onMessageSeen(msgId: String, readerName: String) { onMessageSeen?.invoke(msgId, readerName) }
        override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
        override fun onPublicKeyReceived(senderName: String, senderNodeId: String, key: String) { onPublicKeyReceived?.invoke(senderName, senderNodeId, key) }
        override fun onRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>, topologySequence: Long) {
            onRoutingTableReceived?.invoke(senderName, senderNodeId, connectedNodes, connectedNodeIds, topologySequence)
        }
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
        override fun onBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {
            onBlockRequest?.invoke(endpointId, payload, envelope)
        }
        override fun onBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {
            onBlockAck?.invoke(endpointId, payload, envelope)
        }
        override fun onLegacyBlockControl(payloadType: String, senderName: String) {
            AppLogger.d("BLE_MESH", "Ignoring legacy $payloadType control from $senderName")
        }
        override fun showNotification(sender: String, text: String) { notificationHelper.showPrivateMessageNotification(sender, text) }
        override fun showSosEmergencyNotification(sender: String, text: String) {
            if (!com.example.testresqmesh.MainActivity.isAppInForeground) {
                notificationHelper.showSosEmergencyNotification(sender, text)
            }
        }
    }
    
    val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    val handler = Handler(Looper.getMainLooper())
    private val transferCoordinator = GattTransferCoordinator(store)
    private val heartbeatCoordinator = HeartbeatCoordinator()
    private val HEARTBEAT_ACK_TIMEOUT_MS = 8_000L
    private val GATT_CHUNK_TIMEOUT_MS = 4_000L
    private val gattTransferExecutor = GattTransferExecutor(
        store = store,
        coordinator = transferCoordinator,
        handler = handler,
        serviceUuid = SERVICE_UUID,
        receiveCharacteristicUuid = RX_CHARACTERISTIC_UUID,
        transmitCharacteristicUuid = TX_CHARACTERISTIC_UUID,
        gattServer = { gattServer },
        readyLink = ::readyGattLink,
        hasUsableL2cap = ::hasUsableL2cap,
        promoteToL2cap = ::promoteGattWorkToL2cap,
        resendPayload = ::sendDirectPayload,
        onHeartbeatSent = ::markHeartbeatSent,
        onFlightRemoved = { endpoint -> heartbeatCoordinator.remove(endpoint) },
        isCurrentLink = { link -> store.links.isCurrent(link) },
        disconnectClient = ::forceGattDisconnect,
        disconnectServer = { device ->
            try {
                gattServer?.cancelConnection(device)
            } catch (e: SecurityException) {
                AppLogger.d("BLE_MESH", "SERVER transfer cleanup skipped: BLUETOOTH_CONNECT was revoked")
            }
        },
        chunkTimeoutMs = GATT_CHUNK_TIMEOUT_MS
    )
    private val l2capTransport = L2capTransport(
        store, handler, ::hasLiveSocket, ::processBinaryPayload, ::promoteGattWorkToL2cap,
        ::startHeartbeatChallenge, { endpoint, payload -> enqueueGattPayload(endpoint, payload) },
        { sendSystemPulse(forceFull = true) }
    )
    private val lifecycleSupervisor = BleLifecycleSupervisor(
        store, handler, heartbeatCoordinator, HEARTBEAT_ACK_TIMEOUT_MS, CONNECT_LOCK_MAX_HOLD_MS,
        { releaseConnectLock(null, "stuck lock backstop", force = true) }, ::startHeartbeatChallenge,
        { endpoint -> store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) } },
        { endpoint -> store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) } },
        { endpoint, responsive -> onDeviceLivenessChanged?.invoke(endpoint, responsive) },
        { endpoint -> onDeviceDisconnected?.invoke(endpoint); onDeviceScanRemoved?.invoke(endpoint) },
        ::sendSystemPulse
    )
    private val peerAdmissionController = BlePeerAdmissionController(
        store, handler, { myDeviceName }, ::isDeviceBlocked, ::hasLinkToIdentity,
        { peerName -> checkRouteExists?.invoke(peerName) == true }, ::hasPayloadReadyDirectLink, ::hasReadyLinkToIdentity, ::distinctLinkCount,
        { MAX_TOTAL_CONNECTIONS }, ::getElectionScore, ::latestEndpointForIdentity,
        ::connectToPersistentGatt, { event -> onDeviceScanned?.invoke(event) },
        { endpoint -> onDeviceDisconnected?.invoke(endpoint) }, ::sendSystemPulse,
        { radioController.activeHandshakeInfo() },
        distinctReadyPeerCount = ::distinctReadyLinkCount,
        disconnectEndpoint = ::disconnectFromEndpoint
    )

    fun startMeshNode(teamKey: String) {
        currentTeamKey = teamKey
        isCloaked = false
        if (!radioController.isSupported) {
            onStatusChanged?.invoke("Hardware not fully supported")
            return
        }
        store.isNodeActive.set(true)

        startGattServer()
        startAdvertising(teamKey)
        startScanning()
        lifecycleSupervisor.start()
        onStatusChanged?.invoke("Mesh Active [Persistent GATT/Protobuf]. Seeking peers...")
    }
    
    private var lastSystemPulseHash: Int = 0
    private var lastSystemPulseTime: Long = 0
    // Wall-clock seed keeps versions increasing across ordinary process restarts.
    private var topologySequence: Long = System.currentTimeMillis()
    private var pingCounter: Int = 0

    fun sendSystemPulse(forceFull: Boolean = false) {
        if (!store.isNodeActive.get()) return
        try {
            // Build and sort identity pairs together. Independently sorting names and IDs can bind
            // one peer's display name to another peer's stable identity.
            val neighbors = (store.activeConnections.keys + store.activeServerConnections.keys)
                .distinct()
                .mapNotNull { endpoint ->
                    val name = store.connectedEndpointNames[endpoint]
                    val nodeId = nodeIdForEndpoint(endpoint)?.uppercase()
                    if (name.isNullOrBlank() || NodeIdentity.isPlaceholder(name) || nodeId.isNullOrBlank()) null
                    else nodeId to name
                }
                .distinctBy { it.first }
                .sortedBy { it.first }
            val connectedNodeIds = neighbors.map { it.first }
            val connectedNodesList = neighbors.map { it.second }
            val currentHash = neighbors.hashCode()
            val now = System.currentTimeMillis()
            
            // PING is link liveness only. Refresh the leased topology well before its expiry.
            if (!forceFull && currentHash == lastSystemPulseHash && (now - lastSystemPulseTime < 30000)) {
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
            topologySequence++
            
            val pulseId = java.util.UUID.randomUUID().toString()
            val payload = com.example.testresqmesh.core.network.MeshPayload(
                id = pulseId,
                type = "SYSTEM",
                senderName = myDeviceName,
                connectedNodes = connectedNodesList,
                senderNodeId = myNodeId,
                connectedNodeIds = connectedNodeIds,
                publicKey = com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64(),
                ttl = getMeshProfileTtl(),
                topologySequence = topologySequence
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
        radioController.stop()
        
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
        heartbeatCoordinator.clear()
        store.isWriting.clear()
        lifecycleSupervisor.stop()
        handler.removeCallbacks(updateAdvertisingRunnable)
        lastAdvertisedConnections = -1
        store.connectedEndpointIds.clear()
        store.connectedEndpointNames.clear()
        store.endpointLastSeen.clear()
        store.endpointFirstSeen.clear()
        store.endpointNodeIds.clear()
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

    private var lastAdvertisedConnections: Int = -1
    private val updateAdvertisingRunnable = Runnable {
        if (!store.isNodeActive.get()) return@Runnable
        val currentConnections = distinctLinkCount()
        if (currentConnections != lastAdvertisedConnections) {
            lastAdvertisedConnections = currentConnections
            AppLogger.d("BLE_MESH", "Updating BLE advertisement: directConnections=$currentConnections")
            startAdvertising(currentTeamKey)
        }
    }

    fun scheduleAdvertisingUpdate(delayMs: Long = 1000L) {
        handler.removeCallbacks(updateAdvertisingRunnable)
        handler.postDelayed(updateAdvertisingRunnable, delayMs)
    }

    fun startAdvertising(teamKey: String) {
        lastAdvertisedConnections = distinctLinkCount()
        radioController.startAdvertising(
            electionScore = getElectionScore(),
            directConnections = lastAdvertisedConnections,
            deviceName = myDeviceName,
            fallbackNodeId = myNodeId.ifEmpty { myHex }
        )
    }

    fun startScanning() = radioController.startScanning()

    fun beginRadioHandshake(owner: String) = radioController.beginHandshake(owner)

    fun isRadioHandshakeActive(): Boolean = radioController.isHandshakeActive()

    fun finishRadioHandshake(owner: String, reason: String) =
        radioController.finishHandshake(owner, reason)

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

    private fun handleAdvertisement(advertisement: BleAdvertisement) {
        peerAdmissionController.handle(advertisement)
    }

    fun connectToPersistentGatt(macAddress: String, peerName: String) =
        gattClientManager.connectToPersistentGatt(macAddress, peerName)

    // ---------------------------------------------------------------------------------------------
    // Connect lock
    // ---------------------------------------------------------------------------------------------

    /**
     * Acquires the single-flight outbound connection lock.
     *
     * Android's GATT stack misbehaves badly with concurrent `connectGatt` calls, so outbound
     * connections are deliberately serialised. The lock records its acquisition time so
     * [BleLifecycleSupervisor] can force-release it if a GATT callback is dropped by the OEM stack.
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
        heartbeatCoordinator.remove(endpointId)
        store.isWriting.remove(endpointId)
        store.chunkBuffers.remove(endpointId)
        store.connectionMtu.remove(endpointId)
        store.writeFailureCount.remove(endpointId)
        store.connectionInteractionTimes.remove(endpointId)
        AppLogger.d("BLE_MESH", "Retired unowned endpoint transport $endpointId")
        scheduleAdvertisingUpdate()
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

    /** A direct neighbor is usable only after its GATT role has reached payload READY. */
    private fun hasPayloadReadyDirectLink(): Boolean =
        (store.activeConnections.keys + store.activeServerConnections.keys).any { endpoint ->
            store.links.isReady(endpoint)
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

    fun distinctReadyLinkCount(): Int {
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { store.links.isReady(it) }
        return endpoints
            .map { endpoint ->
                nodeIdForEndpoint(endpoint)
                    ?: NodeIdentity.key(store.connectedEndpointNames[endpoint]).ifEmpty { endpoint }
            }
            .toSet()
            .size
    }


    fun processNextPayload(macAddress: String) = gattTransferExecutor.processNext(macAddress)

    fun completeGattChunk(
        endpoint: String,
        role: BleLinkRole,
        status: Int,
        gatt: BluetoothGatt? = null,
        device: BluetoothDevice? = null
    ) = gattTransferExecutor.complete(endpoint, role, status, gatt, device)
    fun startGattServer() { gattServerManager.startGattServer() }

    fun startL2capServer() { gattServerManager.startL2capServer() }

    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket) =
        l2capTransport.attach(macAddress, socket)

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        if (!hasLiveSocket(endpointId)) {
            AppLogger.d("BLE_MESH", "Dropping payload from unowned endpoint $endpointId")
            return
        }
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            onDeviceLivenessChanged?.invoke(endpointId, true)
            if (payload.type != "PONG") heartbeatCoordinator.remove(endpointId)

            // Inbound central addresses are often unknown at ACL creation. A direct SYSTEM pulse is
            // the first reliable endpoint-to-stable-identity binding; reject it before the peer is
            // published or normal traffic is dispatched. Relayed packets never take this path.
            val isDirectIdentityPulse = payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.senderName.isNotEmpty()
            if (isDirectIdentityPulse && isDeviceBlocked(payload.senderName)) {
                AppLogger.d("BLE_MESH", "Identity gate rejected direct blocked peer ${payload.senderName} on $endpointId")
                handler.post { disconnectDirectIdentity(payload.senderName, "identity gate") }
                return
            }
            
            // Only auto-rename the physical socket if this is a direct message (not relayed).
            // Relayed payloads carry a non-empty routePath; renaming from those would map a remote
            // node onto a local socket and corrupt the routing table.
            if (isDirectIdentityPulse) {
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

    /** SOS/control callers may overtake a queued low-priority attachment frame. */
    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null) {
        cacheOutgoingMessageId(payloadBytes)
        val targets = mutableSetOf<String>()
        targets.addAll(store.activeConnections.keys)
        targets.addAll(store.activeServerConnections.keys)
        targets.remove(excludeEndpointId)
        targets.forEach { sendPriorityPayload(it, payloadBytes) }
    }

    /** Queue one complete framed payload, bypassing L2CAP for a GATT health check. */
    private fun enqueueGattPayload(
        endpoint: String,
        payloadBytes: ByteArray,
        priority: Boolean = false,
        heartbeatId: String? = null
    ): Boolean {
        if (!hasReadyEndpoint(endpoint)) return false
        val frame = MeshFrameCodec.encode(payloadBytes) ?: return false
        if (!transferCoordinator.enqueue(endpoint, GattTransfer(frame, heartbeatId), priority)) {
            AppLogger.d("BLE_MESH", "GATT queue full for $endpoint; rejected payload")
            return false
        }
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
        if (!store.isNodeActive.get() || heartbeatCoordinator.contains(endpoint)) return
        val link = readyGattLink(endpoint) ?: return
        val id = "HB:${UUID.randomUUID()}"
        val challenge = heartbeatCoordinator.begin(endpoint, link.role, link.generation, id) ?: return
        val payload = MeshPayload(id = id, type = "PING", senderName = myDeviceName)
        if (!enqueueGattPayload(endpoint, ProtoBuf.encodeToByteArray(payload), priority = true, heartbeatId = id)) {
            heartbeatCoordinator.remove(endpoint, challenge)
        } else {
            AppLogger.d("BLE_MESH", "Link ${link.generation} $endpoint: queued GATT heartbeat challenge")
        }
    }

    private fun markHeartbeatSent(endpoint: String, link: com.example.testresqmesh.core.network.bluetooth.state.BleLink, id: String) {
        if (!store.links.isCurrent(link)) return
        val sent = heartbeatCoordinator.markSent(endpoint, link.generation, id) ?: return
        handler.postDelayed({
            if (heartbeatCoordinator.pending(endpoint) != sent) return@postDelayed
            val current = store.links.current(endpoint, sent.role)
            if (current == null || current.generation != sent.generation) {
                heartbeatCoordinator.remove(endpoint, sent)
                return@postDelayed
            }
            if (store.connectionInteractionTimes[endpoint]?.let { it > sent.sentAt } == true) {
                heartbeatCoordinator.remove(endpoint, sent)
                return@postDelayed
            }
            if (store.activeL2capSockets.containsKey(endpoint)) {
                heartbeatCoordinator.remove(endpoint, sent)
                return@postDelayed
            }
            if (sent.expired(System.currentTimeMillis(), HEARTBEAT_ACK_TIMEOUT_MS)) {
                heartbeatCoordinator.remove(endpoint, sent)
                AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: GATT heartbeat ACK timed out; retiring silent roles")
                store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) }
                store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) }
            }
        }, HEARTBEAT_ACK_TIMEOUT_MS)
    }

    private fun onHeartbeatAck(endpoint: String, id: String) {
        handler.post {
            val pending = heartbeatCoordinator.pending(endpoint) ?: return@post
            val current = store.links.current(endpoint, pending.role) ?: return@post
            if (store.links.isCurrent(current) && heartbeatCoordinator.acknowledge(endpoint, id, current.generation)) {
                AppLogger.d("BLE_MESH", "Link ${current.generation} $endpoint: GATT heartbeat acknowledged")
            }
        }
    }

    /** Sends a control payload ahead of normal GATT queue traffic; it never opens a new link. */
    fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult {
        if (!BluetoothAdapter.checkBluetoothAddress(targetEndpointId)) return TransportDispatchResult.REJECTED_INVALID_ENDPOINT
        if (!hasReadyEndpoint(targetEndpointId)) return TransportDispatchResult.REJECTED_NOT_READY
        if (l2capTransport.send(targetEndpointId, payloadBytes)) return TransportDispatchResult.ACCEPTED
        return if (enqueueGattPayload(targetEndpointId, payloadBytes, priority = true)) {
            TransportDispatchResult.ACCEPTED
        } else {
            TransportDispatchResult.REJECTED_QUEUE_FULL
        }
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray): TransportDispatchResult {
        if (!BluetoothAdapter.checkBluetoothAddress(targetMacAddress)) return TransportDispatchResult.REJECTED_INVALID_ENDPOINT
        val fullData = MeshFrameCodec.encode(payloadBytes)
        if (fullData == null) {
            AppLogger.d("BLE_MESH", "Rejected oversized or empty outbound payload for $targetMacAddress")
            return TransportDispatchResult.REJECTED_INVALID_FRAME
        }
        
        cacheOutgoingMessageId(payloadBytes)

        if (l2capTransport.send(targetMacAddress, payloadBytes)) return TransportDispatchResult.ACCEPTED
        
        val isServerConnected = store.activeServerConnections.containsKey(targetMacAddress)
        val isClientConnected = store.activeConnections.containsKey(targetMacAddress)
        if (!isServerConnected && !isClientConnected) {
            AppLogger.d("BLE_MESH", "sendDirectPayload skipped: $targetMacAddress is not directly connected; relying on mesh routing")
            return TransportDispatchResult.REJECTED_NOT_READY
        }

        if (!hasReadyEndpoint(targetMacAddress)) return TransportDispatchResult.REJECTED_NOT_READY

        if (!transferCoordinator.enqueue(targetMacAddress, GattTransfer(fullData), priority = false)) {
            AppLogger.d("BLE_MESH", "GATT queue full for $targetMacAddress; rejected payload")
            return TransportDispatchResult.REJECTED_QUEUE_FULL
        }

        processNextPayload(targetMacAddress)
        return TransportDispatchResult.ACCEPTED
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

        val activeHeartbeatId = store.gattFlights[endpoint]?.transfer?.heartbeatId
        val promoted = transferCoordinator.drainForPromotion(endpoint)
        if (activeHeartbeatId != null) heartbeatCoordinator.remove(endpoint)
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
        heartbeatCoordinator.remove(endpointId)
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
        scheduleAdvertisingUpdate()
    }
    
    /** Installs direct-link denial by stable identity without tearing down a live control path. */
    fun denyDirectIdentity(deviceName: String) {
        store.blockedDevices[NodeIdentity.key(deviceName)] = true
    }

    fun releaseDirectIdentity(deviceName: String) {
        store.blockedDevices.remove(NodeIdentity.key(deviceName))
        store.blockedDevices.keys
            .filter { NodeIdentity.matches(it, deviceName) }
            .forEach { store.blockedDevices.remove(it) }
    }

    /** Closes every active client/server/L2CAP endpoint resolved to [deviceName]. */
    fun disconnectDirectIdentity(deviceName: String, reason: String = "direct identity denied") {
        val nodeId = NodeIdentity.idOf(deviceName)
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { endpoint ->
                NodeIdentity.matches(store.connectedEndpointNames[endpoint], deviceName) ||
                    (nodeId != null && nodeIdForEndpoint(endpoint) == nodeId)
            }
            .toSet()
        endpoints.forEach { endpoint ->
            AppLogger.d("BLE_MESH", "Disconnecting $endpoint for $deviceName ($reason)")
            disconnectFromEndpoint(endpoint)
        }
    }

    /** Legacy entry point: deny then immediately retire all known direct endpoints. */
    fun blockDevice(deviceName: String, sendNotification: Boolean = true) {
        denyDirectIdentity(deviceName)
        disconnectDirectIdentity(deviceName, "legacy block")
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
        releaseDirectIdentity(deviceName)
    }
    
    fun rescan() {
        radioController.rescan()
    }
    
    fun forceConnectToDevice(endpointId: String, endpointName: String) {
        if (isDeviceBlocked(endpointName)) {
            AppLogger.d("BLE_MESH", "Connect Directly denied for blocked peer $endpointName")
            return
        }
        if (findLinkEndpointByIdentity(endpointName) != null) {
            AppLogger.d("BLE_MESH", "Connect Directly skipped for $endpointName; a direct link already exists")
            return
        }
        if (distinctLinkCount() >= MAX_TOTAL_CONNECTIONS) {
            AppLogger.d("BLE_MESH", "Connect Directly deferred for $endpointName; direct-link capacity is full")
            return
        }
        AppLogger.d("BLE_MESH", "Connect Directly requested for $endpointName on $endpointId")
        connectToPersistentGatt(endpointId, endpointName)
    }

    fun broadcastSeenReceipt(
        targetMessageId: String,
        isPrivate: Boolean,
        targetId: String? = null,
        directedReturnRoute: List<String> = emptyList()
    ) {
        val payload = MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "SEEN",
            targetMessageId = targetMessageId,
            reader = myDeviceName,
            isPrivate = isPrivate,
            directedRoute = directedReturnRoute,
            directedRouteNodeIds = directedReturnRoute.mapNotNull(NodeIdentity::idOf)
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else if (!isPrivate) {
            broadcastPayload(bytes)
        } else {
            AppLogger.d("BLE_MESH", "Private seen receipt has no directed next hop; not broadcasting")
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
            directedRoute = directedReturnRoute,
            directedRouteNodeIds = directedReturnRoute.mapNotNull(NodeIdentity::idOf)
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)
        
        if (targetId != null) {
            sendDirectPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }
}
