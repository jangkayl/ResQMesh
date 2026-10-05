package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.network.bluetooth.peers.BlePeerDirectory
import com.example.testresqmesh.core.network.bluetooth.peers.BleLinkRetirementController
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportResources
import com.example.testresqmesh.core.network.bluetooth.session.BleTransportSessionController
import com.example.testresqmesh.core.network.bluetooth.session.BleHeartbeatDriver
import com.example.testresqmesh.core.network.dispatch.NativePayloadCallbacks
import com.example.testresqmesh.core.network.dispatch.NativeInboundPipeline
import com.example.testresqmesh.core.network.transport.NativeOutboundDispatcher
import com.example.testresqmesh.core.network.transport.OutboundFrameReporter
import com.example.testresqmesh.core.network.bluetooth.BlePresencePublisher
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.network.bluetooth.BleAvailability
import com.example.testresqmesh.core.network.bluetooth.BleSessionLifecycle
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState
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
import kotlinx.serialization.protobuf.ProtoBuf

@SuppressLint("MissingPermission")
class NativeBleManager(val context: Context) : NativePayloadEvents {
    var onOutboundFrame: ((OutboundFrameEvent) -> Unit)? = null
    override var onConversationMessage: ((String, MeshPayload) -> Unit)? = null
    override var onSosPacket: ((String, MeshPayload) -> Unit)? = null
    val gattServerManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattServerManager(context, this)
    val gattClientManager = com.example.testresqmesh.core.network.bluetooth.gatt.GattClientManager(context, this)
    val store = com.example.testresqmesh.core.network.bluetooth.state.BleStateStore()
    private val peerDirectory = BlePeerDirectory(store, ::isDeviceBlocked)
    var onDeviceConnected: ((ConnectedDevice) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onDeviceLivenessChanged: ((String, Boolean) -> Unit)? = null
    var onDeviceScanned: ((com.example.testresqmesh.core.model.ScanEvent) -> Unit)? = null
    var onDeviceScanRemoved: ((String) -> Unit)? = null
    override var onMessageReceived: MessageReceivedCallback? = null
    override var onMessageSeen: ((String, String) -> Unit)? = null
    override var stpNeighborsProvider: (() -> Set<String>)? = null
    override var onLiveAudioChunk: ((String, String, ByteArray) -> Unit)? = null
    override var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
    override var onPublicKeyReceived: ((String, String, String) -> Unit)? = null
    override var onRoutingTableReceived: ((String, String, List<String>, List<String>, Long) -> Unit)? = null
    override var onSosCancelled: (() -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onTransportStateChanged: ((MeshTransportState) -> Unit)? = null
    var canRetireForBridge: ((String) -> Boolean)? = null
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null
    override var onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    override var onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    override var onDomainEvent: ((String, MeshPayload) -> Unit)? = null
    override var onEventSyncRequest: ((String, MeshPayload) -> Unit)? = null
    override var onEventSyncResponse: ((String, MeshPayload) -> Unit)? = null
    var checkRouteExists: ((String) -> Boolean)? = null

    override var myDeviceName: String = "ResQMesh_Node"
    val myHex = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()

    /**
     * Stable, persisted node ID (SharedPreferences `node_id`) supplied by [MeshRepository.startNode].
     * Advertised as its own field so peer identity survives display-name truncation.
     */
    override var myNodeId: String = ""
    fun getMeshProfileTtl(): Int {
        val isLongRange = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .getBoolean("mesh_profile_long_range", true)
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
        hasReadyConnection = ::hasPayloadReadyDirectLink,
        onAdvertisement = ::handleAdvertisement,
        onAdvertisingFailure = ::retryAdvertising
    )

    private val transportResources = BleTransportResources()
    var gattServer: BluetoothGattServer?
        get() = transportResources.gattServer
        set(value) { transportResources.gattServer = value }
    var l2capServerSocket: android.bluetooth.BluetoothServerSocket?
        get() = transportResources.l2capServerSocket
        set(value) { transportResources.l2capServerSocket = value }
    var myL2capPsm: Int
        get() = transportResources.myL2capPsm
        set(value) { transportResources.myL2capPsm = value }
    var l2capAcceptThread: Thread?
        get() = transportResources.l2capAcceptThread
        set(value) { transportResources.l2capAcceptThread = value }

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

    val payloadDispatcherCallback: PayloadDispatcherCallback = NativePayloadCallbacks(
        events = this,
        seenMessageIds = { store.seenMessageIds },
        endpointForPeer = ::endpointForPeer,
        pendingCustody = ::hasPendingCustody,
        forwardPrivate = ::forwardPrivatePayload,
        direct = ::sendDirectPayload,
        priority = ::sendPriorityPayload,
        receipt = ::queuePrivateReceipt,
        gatt = { endpoint, bytes -> enqueueGattPayload(endpoint, bytes, priority = true) },
        heartbeatAck = ::onHeartbeatAck,
        broadcast = { bytes, excluded -> broadcastPayload(bytes, excluded) },
        disconnect = ::disconnectFromEndpoint,
        privateNotification = notificationHelper::showPrivateMessageNotification,
        sosNotification = notificationHelper::showSosEmergencyNotification
    )

    private fun forwardPrivatePayload(nodeId: String, payload: ByteArray): TransportDispatchResult {
        return outboundDispatcher.forwardPrivatePayload(nodeId, payload)
    }

    val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    val handler = Handler(Looper.getMainLooper())
    private val peerTransferVersions = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val transferIo = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "ResQMesh-transfer-journal").apply { isDaemon = true }
    }
    private val reliableTransfers by lazy {
        ReliableMeshTransfers(
            java.io.File(context.noBackupFilesDir, "mesh-transfers"),
            active = { store.isNodeActive.get() },
            ready = { peer -> peerTransferVersions[peer] == ReliableMeshTransfers.VERSION &&
                endpointForPeer(peer)?.let { store.links.isIdentityAdmitted(it) } == true },
            execute = { action -> transferIo.execute { action() } },
            schedule = { delay, action -> handler.postDelayed({ action() }, delay) },
            send = { peer, bytes, priority, done -> handler.post {
                val endpoint = endpointForPeer(peer)
                done(if (endpoint == null) TransportDispatchResult.REJECTED_NOT_READY else sendRawPayload(endpoint, bytes, priority))
            } },
            received = { peer, bytes -> handler.post { endpointForPeer(peer)?.let { processBinaryPayload(it, bytes) } } },
            response = { peer, bytes -> handler.post { endpointForPeer(peer)?.let { sendRawPayload(it, bytes, true) } } },
            event = { bytes, stage, queuedAt, startedAt -> handler.post {
                reportFrame("", bytes, stage, "TRANSFER", queuedAt, startedAt)
            } },
            log = { AppLogger.d("BLE_TRANSFER", it) }
        )
    }
    private val outboundDispatcher: NativeOutboundDispatcher = NativeOutboundDispatcher(
        store = store,
        handler = handler,
        peerTransferVersions = peerTransferVersions,
        nodeIdForEndpoint = ::nodeIdForEndpoint,
        hasReadyEndpoint = ::hasReadyEndpoint,
        isDeviceBlocked = ::isDeviceBlocked,
        hasUsableL2cap = ::hasUsableL2cap,
        transfers = { reliableTransfers },
        coordinator = { transferCoordinator },
        sendL2cap = { endpoint, bytes, priority -> l2capTransport.send(endpoint, bytes, priority) },
        processNextPayload = ::processNextPayload,
        reportFrame = { endpoint, bytes, stage, transport -> reportFrame(endpoint, bytes, stage, transport) },
        removeHeartbeat = { endpoint -> heartbeatCoordinator.remove(endpoint) }
    )

    fun hasPendingTransfer(messageId: String) = reliableTransfers.hasOutbound(messageId)
    fun hasPendingCustody(payloadId: String) = reliableTransfers.hasPendingCustody(payloadId)
    fun markPayloadStored(messageId: String) = reliableTransfers.stored(messageId)
    fun preferredEndpointForPeer(nodeId: String): String? = endpointForPeer(nodeId.uppercase())

    private fun endpointForPeer(nodeId: String): String? {
        return outboundDispatcher.endpointForPeer(nodeId)
    }

    private fun isControlPayload(bytes: ByteArray): Boolean {
        return outboundDispatcher.isControlPayload(bytes)
    }

    private val frameReporter: OutboundFrameReporter = OutboundFrameReporter(
        readyGattLink = ::readyGattLink,
        nodeIdForEndpoint = ::nodeIdForEndpoint,
        transfers = { reliableTransfers },
        removeLegacyFrame = { key, endpoint -> outboundDispatcher.removeLegacyFrame(key, endpoint) },
        listener = { onOutboundFrame }
    )

    private fun reportFrame(endpoint: String, bytes: ByteArray, stage: OutboundFrameEvent.Stage,
                            transport: String, queuedAt: Long = 0L, startedAt: Long = 0L) {
        frameReporter.reportFrame(endpoint, bytes, stage, transport, queuedAt, startedAt)
    }

    private fun onL2capLost(endpoint: String) {
        gattClientManager.onSocketLost(endpoint)
        if (!hasLiveSocket(endpoint)) return
        val quarantined = BleLinkRole.entries.mapNotNull { store.links.current(endpoint, it) }.filter { it.gattQuarantined }
        if (quarantined.isNotEmpty()) {
            // A failed ATT frame may still exist at the peer. Reopen that generation before reuse.
            AppLogger.d("BLE_TRANSFER", "ATT_RECOVERY_REQUIRED endpoint=$endpoint")
            quarantined.forEach { link ->
                if (link.role == BleLinkRole.CLIENT) forceGattDisconnect(endpoint, link.gatt) else retireServerLink(link)
            }
        }
        if (hasLiveSocket(endpoint)) { outboundDispatcher.flushFallback(); startHeartbeatChallenge(endpoint) }
    }
    private val privateReceipts = DirectedReceiptQueue(
        send = { payload ->
            val ids = payload.directedRouteNodeIds
            val index = ids.indexOf(myNodeId)
            val endpoint = if (index >= 0) ids.getOrNull(index + 1)?.let(payloadDispatcherCallback::getConnectedEndpointIdByNodeId) else null
            if (endpoint == null) TransportDispatchResult.REJECTED_NOT_READY
            else sendPriorityPayload(endpoint, ProtoBuf.encodeToByteArray(payload))
        },
        schedule = { delayMs, action -> handler.postDelayed({ action() }, delayMs) },
        now = System::currentTimeMillis,
        log = { AppLogger.d("MeshNetwork_E2EE", it) }
    )
    fun queuePrivateReceipt(payload: MeshPayload) {
        reliableTransfers.stored(payload.targetMessageId, ProtoBuf.encodeToByteArray(payload))
        handler.post { if (store.isNodeActive.get()) privateReceipts.offer(payload) }
    }
    fun wakePrivateReceipts() { handler.post { if (store.isNodeActive.get()) privateReceipts.wake() } }
    fun showPrivateMessageNotification(sender: String, text: String) = payloadDispatcherCallback.showNotification(sender, text)
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
        resendPayload = ::sendRawPayload,
        onHeartbeatSent = ::markHeartbeatSent,
        onFlightRemoved = { endpoint -> heartbeatCoordinator.remove(endpoint) },
        isCurrentLink = { link -> store.links.isCurrent(link) },
        disconnectClient = ::forceGattDisconnect,
        disconnectServer = ::retireServerLink,
        chunkTimeoutMs = GATT_CHUNK_TIMEOUT_MS,
        onGattIdle = { endpoint -> gattClientManager.onGattIdle(endpoint) },
        onFrame = { endpoint, bytes, stage -> reportFrame(endpoint, bytes, stage, "GATT") }
    )
    private val l2capTransport = L2capTransport(
        store, handler, ::hasLiveSocket, ::processBinaryPayload, ::promoteGattWorkToL2cap,
        ::onL2capLost, { endpoint, payload ->
            outboundDispatcher.retainFailedL2cap(endpoint, payload)
        },
        { sendSystemPulse(forceFull = true) },
        { endpoint, bytes, stage -> reportFrame(endpoint, bytes, stage, "L2CAP") },
        ::isControlPayload
    )
    private val lifecycleSupervisor = BleLifecycleSupervisor(
        store, handler, heartbeatCoordinator, HEARTBEAT_ACK_TIMEOUT_MS, CONNECT_LOCK_MAX_HOLD_MS,
        { releaseConnectLock(null, "stuck lock backstop", force = true) }, ::startHeartbeatChallenge,
        { endpoint -> store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) } },
        { endpoint -> store.links.current(endpoint, BleLinkRole.SERVER)?.let(::retireServerLink) },
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
        disconnectEndpoint = ::disconnectFromEndpoint,
        isNodeActive = { store.isNodeActive.get() },
        canRetireForBridge = { endpoint -> canRetireForBridge?.invoke(endpoint) == true },
        isTransportIdle = { endpoint -> l2capTransport.isIdle(endpoint) && !outboundDispatcher.hasPendingFallback(endpoint) &&
            nodeIdForEndpoint(endpoint)?.let { !reliableTransfers.hasOutboundTo(it.uppercase()) } != false }
    )

    private val sessionController: BleTransportSessionController = BleTransportSessionController(
        context, bluetoothAdapter, store, handler, transportResources, radioController,
        peerAdmissionController, lifecycleSupervisor, l2capTransport, { gattClientManager.clearSetups() },
        outboundDispatcher, privateReceipts, heartbeatCoordinator, { reliableTransfers },
        { presencePublisher }, ::distinctReadyLinkCount, ::distinctLinkCount, ::startGattServer,
        ::getElectionScore, ::releaseConnectLock, { currentTeamKey }, { currentTeamKey = it },
        { isCloaked }, { isCloaked = it }, { myDeviceName }, { myNodeId }, myHex, { onStatusChanged },
        { onTransportStateChanged }, { onDeviceDisconnected }, { onDeviceScanRemoved }
    )
    private val sessionLifecycle get() = sessionController.sessionLifecycle
    private val serviceReady get() = sessionController.isServiceReady
    fun trackPendingL2capSocket(socket: BluetoothSocket, generation: Long): Boolean = sessionController.trackPendingL2capSocket(socket, generation)
    fun finishPendingL2capSocket(socket: BluetoothSocket) = sessionController.finishPendingL2capSocket(socket)
    private fun retryAdvertising() = sessionController.retryAdvertising()
    val transportGeneration: Long get() = sessionLifecycle.generation
    var serverRegistrationGeneration: Long
        get() = transportResources.serverRegistrationGeneration
        private set(value) { transportResources.serverRegistrationGeneration = value }
    fun beginServerRegistration(): Long {
        serverRegistrationGeneration++
        store.serverIndications.reset(serverRegistrationGeneration)
        return serverRegistrationGeneration
    }
    fun ownsServerRegistration(generation: Long) = serverRegistrationGeneration == generation
    fun isTransportGenerationCurrent(generation: Long) = sessionLifecycle.owns(generation)
    private fun bluetoothAvailability(): BleAvailability = sessionController.bluetoothAvailability()

    fun reconcileTransport() = sessionController.reconcileTransport()

    fun startMeshNode(teamKey: String) = sessionController.startMeshNode(teamKey)

    private fun startTransport(generation: Long) = sessionController.startTransport(generation)

    fun onGattServerReady(generation: Long) = sessionController.onGattServerReady(generation)

    fun failTransport(generation: Long) = sessionController.failTransport(generation)

    private val presencePublisher = BlePresencePublisher(
        store = store,
        updatePeerCount = { sessionLifecycle.peers(distinctReadyLinkCount()) },
        nodeIdForEndpoint = ::nodeIdForEndpoint,
        hasReadyEndpoint = ::hasReadyEndpoint,
        isDeviceBlocked = ::isDeviceBlocked,
        myDeviceName = { myDeviceName },
        myNodeId = { myNodeId },
        publicKey = CryptoManager::getMyPublicKeyBase64,
        getMeshProfileTtl = ::getMeshProfileTtl,
        broadcastPriorityPayload = { bytes -> broadcastPriorityPayload(bytes) }
    )

    fun sendSystemPulse(forceFull: Boolean = false) = presencePublisher.publish(forceFull)

    fun stopMeshNode() = sessionController.stopMeshNode()

    private fun stopTransport() = sessionController.stopTransport()

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

    fun scheduleAdvertisingUpdate(delayMs: Long = 1000L) = sessionController.scheduleAdvertisingUpdate(delayMs)

    fun startAdvertising(teamKey: String) = sessionController.startAdvertising(teamKey)

    fun startScanning() = radioController.startScanning()

    fun beginRadioHandshake(owner: String) = radioController.beginHandshake(owner)

    fun isRadioHandshakeActive(): Boolean = radioController.isHandshakeActive()

    fun finishRadioHandshake(owner: String, reason: String) =
        radioController.finishHandshake(owner, reason)

    fun latestEndpointForIdentity(peerName: String, fallback: String): String = peerDirectory.latestEndpointForIdentity(peerName, fallback)

    private fun handleAdvertisement(advertisement: BleAdvertisement) {
        if (!store.isNodeActive.get() || !serviceReady) return
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
    fun hasLiveSocket(endpointId: String): Boolean = peerDirectory.hasLiveSocket(endpointId)

    fun hasReadyEndpoint(endpointId: String): Boolean = peerDirectory.hasReadyEndpoint(endpointId)

    /** Retire the data socket and compatibility state only after the last GATT role ends. */
    private val linkRetirement: BleLinkRetirementController = BleLinkRetirementController(
        store, handler, transportResources, peerDirectory, outboundDispatcher, { reliableTransfers },
        l2capTransport, transferCoordinator, heartbeatCoordinator, radioController,
        peerAdmissionController, { sessionLifecycle }, ::finishRadioHandshake, ::releaseConnectLock,
        { scheduleAdvertisingUpdate() }, ::renewIdleGattServerIfNeeded, { sendSystemPulse() },
        { endpoint, bytes, stage, transport -> reportFrame(endpoint, bytes, stage, transport) },
        { onDeviceDisconnected }
    )

    fun cleanupEndpointIfUnowned(endpointId: String) = linkRetirement.cleanupEndpointIfUnowned(endpointId)

    /** Invalidate local ownership before Android cancellation, even if its callback never arrives. */
    fun retireServerLink(link: com.example.testresqmesh.core.network.bluetooth.state.BleLink) = linkRetirement.retireServerLink(link)

    private fun renewIdleGattServerIfNeeded() = sessionController.renewIdleGattServerIfNeeded()

    fun hasReadyLinkToIdentity(peerName: String): Boolean = peerDirectory.hasReadyLinkToIdentity(peerName)

    /** A direct neighbor is usable only after its GATT role has reached payload READY. */
    private fun hasPayloadReadyDirectLink(): Boolean = peerDirectory.hasPayloadReadyDirectLink()

    private fun isUsableDirectNeighbor(endpoint: String): Boolean = peerDirectory.isUsableDirectNeighbor(endpoint)

    /** When this link was established, or [Long.MAX_VALUE] when unknown. */
    fun linkEstablishedAt(endpointId: String): Long = peerDirectory.linkEstablishedAt(endpointId)

    /** The stable node ID associated with an endpoint, from its name or its advertisement. */
    fun nodeIdForEndpoint(endpointId: String): String? = peerDirectory.nodeIdForEndpoint(endpointId)

    /**
     * Finds an existing live socket belonging to the same node as [peerName], regardless of which
     * MAC it was established on.
     *
     * Android advertises on a different MAC than it connects with, so every MAC-keyed duplicate
     * check was silently defeated: a peer already connected inbound on its Central MAC looked
     * absent when matched against its advertising MAC, and we would dial a second redundant link
     * to the same device.
     */
    fun findLinkEndpointByIdentity(peerName: String): String? = peerDirectory.findLinkEndpointByIdentity(peerName)

    fun hasLinkToIdentity(peerName: String): Boolean = peerDirectory.hasLinkToIdentity(peerName)

    /**
     * Number of connected *peers*, not sockets.
     *
     * `activeConnections.size + activeServerConnections.size` double-counted a peer that was
     * linked on two MACs, so a node could believe it was at capacity while actually holding a
     * single duplicated link.
     */
    fun distinctLinkCount(): Int = peerDirectory.distinctLinkCount()

    fun distinctReadyLinkCount(): Int = peerDirectory.distinctReadyLinkCount()


    fun processNextPayload(macAddress: String) = gattTransferExecutor.processNext(macAddress)

    fun completeGattChunk(
        endpoint: String,
        role: BleLinkRole,
        status: Int,
        gatt: BluetoothGatt? = null,
        device: BluetoothDevice? = null
    ) = gattTransferExecutor.complete(endpoint, role, status, gatt, device)
    fun completeServerIndication(ticket: com.example.testresqmesh.core.network.bluetooth.state.ServerIndicationLedger.Ticket, status: Int) =
        gattTransferExecutor.completeServer(ticket, status)
    fun startGattServer() { gattServerManager.startGattServer() }

    fun startL2capServer() { gattServerManager.startL2capServer() }

    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket) =
        l2capTransport.attach(macAddress, socket)

    private val inboundPipeline: NativeInboundPipeline = NativeInboundPipeline(
        store = store,
        handler = handler,
        peerTransferVersions = peerTransferVersions,
        generation = { transportGeneration },
        nodeIdentity = { myNodeId },
        isTransportGenerationCurrent = ::isTransportGenerationCurrent,
        hasLiveSocket = ::hasLiveSocket,
        nodeIdForEndpoint = ::nodeIdForEndpoint,
        hasReadyEndpoint = ::hasReadyEndpoint,
        isDeviceBlocked = ::isDeviceBlocked,
        transfers = { reliableTransfers },
        removeHeartbeat = { endpoint -> heartbeatCoordinator.remove(endpoint) },
        disconnectFromEndpoint = ::disconnectFromEndpoint,
        disconnectDirectIdentity = ::disconnectDirectIdentity,
        sendSystemPulse = { sendSystemPulse() },
        connectedListener = { onDeviceConnected },
        livenessListener = { onDeviceLivenessChanged },
        dispatch = { endpoint, bytes -> payloadDispatcher.dispatch(endpoint, bytes) }
    )

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        inboundPipeline.processBinaryPayload(endpointId, payloadBytes)
    }

    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        return outboundDispatcher.broadcastPayload(payloadBytes, excludeEndpointId)
    }

    /** SOS/control callers may overtake a queued low-priority attachment frame. */
    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        return outboundDispatcher.broadcastPriorityPayload(payloadBytes, excludeEndpointId)
    }

    /** Queue one complete framed payload, bypassing L2CAP for a GATT health check. */
    private fun enqueueGattPayload(
        endpoint: String,
        payloadBytes: ByteArray,
        priority: Boolean = false,
        heartbeatId: String? = null
    ): Boolean {
        return outboundDispatcher.enqueueGattPayload(endpoint, payloadBytes, priority, heartbeatId)
    }

    private fun readyGattLink(endpoint: String) =
        store.links.current(endpoint, BleLinkRole.CLIENT)?.takeIf {
            it.state == BleLinkState.READY && !it.gattQuarantined && it.currentOperation == null && store.activeConnections.containsKey(endpoint)
        } ?: store.links.current(endpoint, BleLinkRole.SERVER)?.takeIf {
            it.state == BleLinkState.READY && !it.gattQuarantined && store.activeServerConnections.containsKey(endpoint)
        }

    private val heartbeatDriver = BleHeartbeatDriver(
        store, handler, heartbeatCoordinator, HEARTBEAT_ACK_TIMEOUT_MS, ::readyGattLink,
        ::enqueueGattPayload, ::forceGattDisconnect, transportResources, { myDeviceName }
    )

    private fun startHeartbeatChallenge(endpoint: String) = heartbeatDriver.startHeartbeatChallenge(endpoint)

    private fun markHeartbeatSent(endpoint: String, link: com.example.testresqmesh.core.network.bluetooth.state.BleLink, id: String) = heartbeatDriver.markHeartbeatSent(endpoint, link, id)

    private fun onHeartbeatAck(endpoint: String, id: String) = heartbeatDriver.onHeartbeatAck(endpoint, id)

    /** Sends a control payload ahead of normal GATT queue traffic; it never opens a new link. */
    fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult {
        return outboundDispatcher.sendPriorityPayload(targetEndpointId, payloadBytes)
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray): TransportDispatchResult {
        return outboundDispatcher.sendDirectPayload(targetMacAddress, payloadBytes)
    }

    /** Only complete wire frames enter a channel; transfer pieces bypass recursive fragmentation. */
    private fun sendRawPayload(endpoint: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        return outboundDispatcher.sendRawPayload(endpoint, bytes, priority)
    }

    fun cacheOutgoingMessageId(payloadBytes: ByteArray) {
        outboundDispatcher.cacheOutgoingMessageId(payloadBytes)
    }

    fun forceGattDisconnect(macAddress: String, gatt: BluetoothGatt?) = linkRetirement.forceGattDisconnect(macAddress, gatt)

    private fun hasUsableL2cap(endpoint: String): Boolean =
        l2capTransport.isUsable(endpoint) && hasLiveSocket(endpoint)

    /**
     * Hands queued complete frames to L2CAP only after the callback-owned GATT frame finishes.
     */
    private fun promoteGattWorkToL2cap(endpoint: String) {
        outboundDispatcher.promoteGattWorkToL2cap(endpoint)
    }

    fun disconnectFromEndpoint(endpointId: String) = linkRetirement.disconnectFromEndpoint(endpointId)

    /** Installs direct-link denial by stable identity without tearing down a live control path. */
    fun denyDirectIdentity(deviceName: String) = linkRetirement.denyDirectIdentity(deviceName)

    fun releaseDirectIdentity(deviceName: String) = linkRetirement.releaseDirectIdentity(deviceName)

    /** Closes every active client/server/L2CAP endpoint resolved to [deviceName]. */
    fun disconnectDirectIdentity(deviceName: String, reason: String = "direct identity denied") = linkRetirement.disconnectDirectIdentity(deviceName, reason)

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
    fun isDeviceBlocked(deviceName: String): Boolean = linkRetirement.isDeviceBlocked(deviceName)

    fun unblockDevice(deviceName: String) {
        releaseDirectIdentity(deviceName)
    }

    fun rescan() = sessionController.rescan()

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

        if (isPrivate) {
            queuePrivateReceipt(payload)
        } else if (targetId != null) {
            sendPriorityPayload(targetId, bytes)
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
            directedRoute = directedReturnRoute,
            directedRouteNodeIds = directedReturnRoute.mapNotNull(NodeIdentity::idOf)
        )
        val bytes = ProtoBuf.encodeToByteArray(payload)

        if (isPrivate) {
            queuePrivateReceipt(payload)
        } else if (targetId != null) {
            sendPriorityPayload(targetId, bytes)
        } else {
            broadcastPayload(bytes)
        }
    }
}
