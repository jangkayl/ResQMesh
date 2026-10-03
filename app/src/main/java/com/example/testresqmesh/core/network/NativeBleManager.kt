package com.example.testresqmesh.core.network

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
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

@SuppressLint("MissingPermission")
class NativeBleManager(val context: Context) {
    var onOutboundFrame: ((OutboundFrameEvent) -> Unit)? = null
    var onConversationMessage: ((String, MeshPayload) -> Unit)? = null
    var onSosPacket: ((String, MeshPayload) -> Unit)? = null
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
    var onTransportStateChanged: ((MeshTransportState) -> Unit)? = null
    var canRetireForBridge: ((String) -> Boolean)? = null
    var onDeviceBlocked: ((String) -> Unit)? = null
    var onDeviceUnblocked: ((String) -> Unit)? = null
    var onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    var onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
    var onDomainEvent: ((String, MeshPayload) -> Unit)? = null
    var onEventSyncRequest: ((String, MeshPayload) -> Unit)? = null
    var onEventSyncResponse: ((String, MeshPayload) -> Unit)? = null
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
        override fun onConversationMessage(endpointId: String, payload: MeshPayload) {
            this@NativeBleManager.onConversationMessage?.invoke(endpointId, payload)
        }
        override fun onSosPacket(endpointId: String, payload: MeshPayload) {
            this@NativeBleManager.onSosPacket?.invoke(endpointId, payload)
        }
        override fun getMyDeviceName() = myDeviceName
        override fun getMyNodeId() = myNodeId
        override fun getSeenMessageIds() = store.seenMessageIds
        override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
        override fun getConnectedEndpointIdByName(name: String) = NodeIdentity.idOf(name)?.let(::endpointForPeer)
        override fun getConnectedEndpointIdByNodeId(nodeId: String) = endpointForPeer(nodeId)
        override fun hasPendingCustody(payloadId: String) = this@NativeBleManager.hasPendingCustody(payloadId)
        override fun forwardPrivatePayload(nodeId: String, payload: ByteArray): TransportDispatchResult {
            return if (peerTransferVersions[nodeId.uppercase()] == ReliableMeshTransfers.VERSION) {
                reliableTransfers.offer(nodeId.uppercase(), payload, false, durableRelay = true)
            } else {
                val endpoint = endpointForPeer(nodeId) ?: return TransportDispatchResult.REJECTED_NOT_READY
                sendLegacyRelay(nodeId.uppercase(), endpoint, payload)
            }
        }
        override fun getStpNeighbors(): Set<String> {
            return this@NativeBleManager.stpNeighborsProvider?.invoke() ?: emptySet()
        }
        override fun sendDirectPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendDirectPayload(endpointId, payload)
        override fun sendPriorityPayload(endpointId: String, payload: ByteArray) = this@NativeBleManager.sendPriorityPayload(endpointId, payload)
        override fun sendPrivateReceipt(payload: MeshPayload) = this@NativeBleManager.queuePrivateReceipt(payload)
        override fun sendGattPayload(endpointId: String, payload: ByteArray) {
            this@NativeBleManager.enqueueGattPayload(endpointId, payload, priority = true)
        }
        override fun onHeartbeatAck(endpointId: String, challengeId: String) = this@NativeBleManager.onHeartbeatAck(endpointId, challengeId)
        override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) { this@NativeBleManager.broadcastPayload(payload, excludeEndpointId) }
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
        override fun onDomainEvent(endpointId: String, payload: MeshPayload) {
            this@NativeBleManager.onDomainEvent?.invoke(endpointId, payload)
        }
        override fun onEventSyncRequest(endpointId: String, payload: MeshPayload) {
            this@NativeBleManager.onEventSyncRequest?.invoke(endpointId, payload)
        }
        override fun onEventSyncResponse(endpointId: String, payload: MeshPayload) {
            this@NativeBleManager.onEventSyncResponse?.invoke(endpointId, payload)
        }
    }
    
    val payloadDispatcher = PayloadDispatcher(payloadDispatcherCallback)
    val handler = Handler(Looper.getMainLooper())
    private val peerTransferVersions = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val legacyRelayFrames = java.util.concurrent.ConcurrentHashMap<String, String>()
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
    private data class FallbackFrame(val bytes: ByteArray, val priority: Boolean)
    private val retainedFallback = com.example.testresqmesh.core.network.bluetooth.state.RetainedFallbackQueue<FallbackFrame>(
        send = { endpoint, frame -> enqueueGattPayload(endpoint, frame.bytes, frame.priority) },
        schedule = { delay, action -> handler.postDelayed({ action() }, delay) },
        failed = { endpoint, frame -> reportFrame(endpoint, frame.bytes, OutboundFrameEvent.Stage.FAILED, "GATT") }
    )
    fun hasPendingTransfer(messageId: String) = reliableTransfers.hasOutbound(messageId)
    fun hasPendingCustody(payloadId: String) = reliableTransfers.hasPendingCustody(payloadId)
    fun markPayloadStored(messageId: String) = reliableTransfers.stored(messageId)
    fun preferredEndpointForPeer(nodeId: String): String? = endpointForPeer(nodeId.uppercase())

    private fun endpointForPeer(nodeId: String): String? = DirectSendPolicy.select(
        (store.activeConnections.keys + store.activeServerConnections.keys).distinct().mapNotNull { endpoint ->
            val id = nodeIdForEndpoint(endpoint) ?: return@mapNotNull null
            val name = store.connectedEndpointNames[endpoint] ?: return@mapNotNull null
            if (!id.equals(nodeId, true) || !hasReadyEndpoint(endpoint) || NodeIdentity.isPlaceholder(name) || isDeviceBlocked(name)) null
            else DirectEndpoint(endpoint, id, hasUsableL2cap(endpoint),
                !com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy.isUnresponsive(
                    store.connectionInteractionTimes[endpoint] ?: 0, System.currentTimeMillis()))
        }
    )?.endpoint

    private fun isControlPayload(bytes: ByteArray): Boolean = runCatching {
        val p = ProtoBuf.decodeFromByteArray<MeshPayload>(bytes)
        p.type in setOf("SYSTEM", "PING", "PONG", "SEEN", "DELIVERED", "BLOCK_REQUEST", "BLOCK_ACK",
            "SOS_EVENT", "SOS_SYNC_REQ", "SOS_SYNC_RES", "EVENT_SYNC_REQ", ReliableMeshTransfers.ACK) ||
            (p.type == ReliableMeshTransfers.PIECE && ProtoBuf.decodeFromByteArray<TransferPiece>(p.liveAudioChunk!!).let { it.priority || it.status })
    }.getOrDefault(false)

    private fun reportFrame(endpoint: String, bytes: ByteArray, stage: OutboundFrameEvent.Stage,
                            transport: String, queuedAt: Long = 0L, startedAt: Long = 0L) {
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull() ?: return
        if (payload.type == ReliableMeshTransfers.PIECE) { reliableTransfers.frame(payload, stage); return }
        if (payload.type !in setOf("MESSAGE", "CONVERSATION")) return
        val now = System.currentTimeMillis()
        val event = OutboundFrameEvent(payload.id, payload.targetMessageId.ifBlank { payload.id }, stage, transport,
            readyGattLink(endpoint)?.generation ?: 0L, android.os.SystemClock.elapsedRealtime(), bytes.size,
            if (queuedAt > 0 && startedAt > 0) (startedAt - queuedAt).coerceAtLeast(0) else 0,
            if (startedAt > 0) (now - startedAt).coerceAtLeast(0) else 0,
            payload.isPrivate, (payload.directedRouteNodeIds.size - 1).coerceAtLeast(1))
        AppLogger.d("BLE_TRANSFER", "FRAME_${stage.name} transmission=${event.transmissionId} message=${event.messageId} transport=$transport bytes=${event.bytes} queueMs=${event.queueMs} transferMs=${event.transferMs}")
        if (stage == OutboundFrameEvent.Stage.COMPLETED && transport != "TRANSFER") {
            nodeIdForEndpoint(endpoint)?.let { peer ->
                if (legacyRelayFrames.remove("${peer.uppercase()}:${payload.id}", endpoint)) {
                    reliableTransfers.legacyForwarded(peer.uppercase(), payload)
                }
            }
        } else if (stage == OutboundFrameEvent.Stage.FAILED && transport != "TRANSFER") {
            nodeIdForEndpoint(endpoint)?.let { legacyRelayFrames.remove("${it.uppercase()}:${payload.id}", endpoint) }
        }
        onOutboundFrame?.invoke(event)
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
        if (hasLiveSocket(endpoint)) { retainedFallback.flush(); startHeartbeatChallenge(endpoint) }
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
            retainedFallback.retain(endpoint, listOf(FallbackFrame(payload, isControlPayload(payload))))
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
        isTransportIdle = { endpoint -> l2capTransport.isIdle(endpoint) && !retainedFallback.hasPending(endpoint) &&
            nodeIdForEndpoint(endpoint)?.let { !reliableTransfers.hasOutboundTo(it.uppercase()) } != false }
    )

    private val sessionLifecycle = BleSessionLifecycle(
        startTransport = ::startTransport,
        stopTransport = ::stopTransport,
        publishState = { state ->
            AppLogger.d("BLE_RECOVERY", "transport=${state.name} generation=$transportGeneration")
            onTransportStateChanged?.invoke(state)
            onStatusChanged?.invoke(state.status)
        }
    )
    val transportGeneration: Long get() = sessionLifecycle.generation
    @Volatile var serverRegistrationGeneration = 0L
        private set
    fun beginServerRegistration(): Long {
        serverRegistrationGeneration++
        store.serverIndications.reset(serverRegistrationGeneration)
        return serverRegistrationGeneration
    }
    fun ownsServerRegistration(generation: Long) = serverRegistrationGeneration == generation
    fun isTransportGenerationCurrent(generation: Long) = sessionLifecycle.owns(generation)
    private var receiverRegistered = false
    private var serviceReady = false
    private var restartAttempts = 0
    private var advertisingAttempts = 0
    private val pendingL2capSockets = mutableSetOf<BluetoothSocket>()
    fun trackPendingL2capSocket(socket: BluetoothSocket, generation: Long): Boolean {
        if (!isTransportGenerationCurrent(generation)) {
            try { socket.close() } catch (_: Exception) {}
            return false
        }
        pendingL2capSockets.add(socket)
        return true
    }
    fun finishPendingL2capSocket(socket: BluetoothSocket) { pendingL2capSockets.remove(socket) }
    private val advertisingRetry = Runnable {
        if (store.isNodeActive.get() && serviceReady) startAdvertising(currentTeamKey)
    }
    private fun retryAdvertising() {
        advertisingAttempts++
        handler.removeCallbacks(advertisingRetry)
        handler.postDelayed(advertisingRetry,
            com.example.testresqmesh.core.network.bluetooth.BleScanRecoveryPolicy.retryDelay(advertisingAttempts) +
                kotlin.random.Random.nextLong(2_001L))
    }
    private val startupTimeout = Runnable { failTransport(transportGeneration) }
    private val recoveryCheck = object : Runnable {
        override fun run() {
            if (!sessionLifecycle.requested) return
            val availability = bluetoothAvailability()
            sessionLifecycle.reconcile(availability)
            if (sessionLifecycle.state == MeshTransportState.ERROR && availability == BleAvailability.AVAILABLE) return
            if (serviceReady && sessionLifecycle.running) {
                sessionLifecycle.peers(distinctReadyLinkCount())
                radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
                radioController.startScanning()
                peerAdmissionController.recover()
            }
            handler.removeCallbacks(this)
            handler.postDelayed(this, 5_000L)
        }
    }
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            handler.post {
                if (!sessionLifecycle.requested) return@post
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state != BluetoothAdapter.ERROR) {
                    // Read actual state: an old OFF broadcast may arrive after a rapid ON.
                    reconcileTransport()
                }
            }
        }
    }

    private fun bluetoothAvailability(): BleAvailability {
        fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (!allowed(Manifest.permission.BLUETOOTH_CONNECT) || !allowed(Manifest.permission.BLUETOOTH_SCAN) ||
                !allowed(Manifest.permission.BLUETOOTH_ADVERTISE))) return BleAvailability.PERMISSION_REQUIRED
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !allowed(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return BleAvailability.PERMISSION_REQUIRED
        }
        return try {
            when {
                bluetoothAdapter == null -> BleAvailability.UNSUPPORTED
                !bluetoothAdapter.isEnabled -> BleAvailability.BLUETOOTH_OFF
                else -> BleAvailability.AVAILABLE
            }
        } catch (_: SecurityException) { BleAvailability.PERMISSION_REQUIRED }
    }

    fun reconcileTransport() {
        handler.post {
            if (!sessionLifecycle.requested) return@post
            handler.removeCallbacks(recoveryCheck)
            recoveryCheck.run()
        }
    }

    fun startMeshNode(teamKey: String) {
        handler.post {
            currentTeamKey = teamKey
            isCloaked = false
            if (!receiverRegistered) {
                ContextCompat.registerReceiver(context.applicationContext, bluetoothStateReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                receiverRegistered = true
            }
            sessionLifecycle.start(bluetoothAvailability())
            if (sessionLifecycle.state != MeshTransportState.ERROR) {
                handler.removeCallbacks(recoveryCheck)
                handler.postDelayed(recoveryCheck, 5_000L)
            }
        }
    }

    private fun startTransport(generation: Long) {
        store.isNodeActive.set(true)
        reliableTransfers.wake()
        serviceReady = false
        lastSystemPulseHash = 0
        lastSystemPulseTime = 0L
        handler.removeCallbacks(startupTimeout)
        handler.postDelayed(startupTimeout, 5_000L)
        try {
            startGattServer()
        } catch (_: Exception) { failTransport(generation) }
    }

    fun onGattServerReady(generation: Long) {
        if (!isTransportGenerationCurrent(generation) || serviceReady) return
        handler.removeCallbacks(startupTimeout)
        serviceReady = true
        restartAttempts = 0
        advertisingAttempts = 0
        sessionLifecycle.ready(generation)
        sessionLifecycle.peers(distinctReadyLinkCount())
        startAdvertising(currentTeamKey)
        startScanning()
        lifecycleSupervisor.start()
    }

    fun failTransport(generation: Long) {
        if (!isTransportGenerationCurrent(generation)) return
        sessionLifecycle.failed(generation)
        restartAttempts++
        handler.removeCallbacks(recoveryCheck)
        val delay = com.example.testresqmesh.core.network.bluetooth.BleScanRecoveryPolicy.retryDelay(restartAttempts) +
            kotlin.random.Random.nextLong(2_001L)
        handler.postDelayed(recoveryCheck, delay)
    }
    
    private var lastSystemPulseHash: Int = 0
    private var lastSystemPulseTime: Long = 0
    // Wall-clock seed keeps versions increasing across ordinary process restarts.
    private var topologySequence: Long = System.currentTimeMillis()
    private var pingCounter: Int = 0

    fun sendSystemPulse(forceFull: Boolean = false) {
        if (!store.isNodeActive.get()) return
        sessionLifecycle.peers(distinctReadyLinkCount())
        try {
            // Build and sort identity pairs together. Independently sorting names and IDs can bind
            // one peer's display name to another peer's stable identity.
            val neighbors = (store.activeConnections.keys + store.activeServerConnections.keys)
                .distinct()
                .mapNotNull { endpoint ->
                    val name = store.connectedEndpointNames[endpoint]
                    val nodeId = nodeIdForEndpoint(endpoint)?.uppercase()
                    if (!hasReadyEndpoint(endpoint) || name.isNullOrBlank() || NodeIdentity.isPlaceholder(name) ||
                        nodeId.isNullOrBlank() || isDeviceBlocked(name)) null
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
                broadcastPriorityPayload(payloadBytes)
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
                topologySequence = topologySequence,
                transferProtocol = ReliableMeshTransfers.VERSION
            )
            val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)
            AppLogger.event(
                category = com.example.testresqmesh.core.utils.TerminalLogCategory.SYNC,
                event = "SYSTEM_SENT",
                message = "Sending full SYSTEM pulse to ${store.activeConnections.size + store.activeServerConnections.size} GATT endpoints",
                tag = "BLE_MESH"
            )
            broadcastPriorityPayload(payloadBytes)
        } catch (e: Exception) {
            AppLogger.d("BLE_MESH", "Failed to send system pulse: ${e.message}")
        }
    }

    fun stopMeshNode() {
        handler.post {
            handler.removeCallbacks(recoveryCheck)
            sessionLifecycle.stop()
            if (receiverRegistered) {
                context.applicationContext.unregisterReceiver(bluetoothStateReceiver)
                receiverRegistered = false
            }
        }
    }

    private fun stopTransport() {
        store.isNodeActive.set(false)
        reliableTransfers.pause()
        retainedFallback.clear()
        legacyRelayFrames.clear()
        gattClientManager.clearSetups()
        privateReceipts.clear()
        serviceReady = false
        handler.removeCallbacks(startupTimeout)
        handler.removeCallbacks(advertisingRetry)
        pendingL2capSockets.forEach { try { it.close() } catch (_: Exception) {} }
        pendingL2capSockets.clear()
        peerAdmissionController.stop()
        val retiringLinks = store.links.snapshot()
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys + store.connectedEndpointIds +
            retiringLinks.map { it.endpoint }).toSet()
        AppLogger.clearLinks()
        radioController.stop()
        
        store.links.clear()
        
        store.activeServerConnections.values.forEach { try { gattServer?.cancelConnection(it) } catch (_: Exception) {} }
        store.activeServerConnections.clear()
        serverRegistrationGeneration++
        store.serverIndications.reset(serverRegistrationGeneration)
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        
        try {
            l2capServerSocket?.close()
            l2capAcceptThread?.interrupt()
        } catch (e: Exception) {}
        l2capServerSocket = null
        l2capAcceptThread = null
        myL2capPsm = 0
        
        (store.activeConnections.values + retiringLinks.mapNotNull { it.gatt }).distinct().forEach {
            try { it.disconnect() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        store.activeConnections.clear()
        l2capTransport.stop()
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
        store.endpointLastScore.clear()
        store.connectionEstablishTime.clear()
        store.connectionInteractionTimes.clear()
        store.l2capOutboundProgressTimes.clear()
        store.chunkBuffers.clear()
        store.connectionMtu.clear()
        store.writeFailureCount.clear()
        store.orphanDetectionTime.clear()
        releaseConnectLock(null, "transport suspended", force = true)
        endpoints.forEach { endpoint ->
            onDeviceDisconnected?.invoke(endpoint)
            onDeviceScanRemoved?.invoke(endpoint)
        }
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
        if (!store.isNodeActive.get() || !serviceReady) return
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
    fun hasLiveSocket(endpointId: String): Boolean =
        store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)

    fun hasReadyEndpoint(endpointId: String): Boolean =
        hasLiveSocket(endpointId) && store.links.isReady(endpointId)

    /** Retire the data socket and compatibility state only after the last GATT role ends. */
    fun cleanupEndpointIfUnowned(endpointId: String) {
        if (hasLiveSocket(endpointId)) return
        legacyRelayFrames.entries.removeAll { it.value == endpointId }
        nodeIdForEndpoint(endpointId)?.uppercase()?.let { reliableTransfers.wake(it) }
        l2capTransport.disconnect(endpointId)
        retainedFallback.retire(endpointId)
        store.gattFlights[endpointId]?.transfer?.let { reportFrame(endpointId, it.payloadBytes(), OutboundFrameEvent.Stage.FAILED, "GATT") }
        store.pendingQueues[endpointId]?.forEach { reportFrame(endpointId, it.payloadBytes(), OutboundFrameEvent.Stage.FAILED, "GATT") }
        transferCoordinator.forget(endpointId)
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
        handler.post {
            if (!store.isNodeActive.get()) return@post
            sessionLifecycle.peers(distinctReadyLinkCount())
            radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
            radioController.startScanning()
            peerAdmissionController.recover()
            renewIdleGattServerIfNeeded()
        }
    }

    /** Invalidate local ownership before Android cancellation, even if its callback never arrives. */
    fun retireServerLink(link: com.example.testresqmesh.core.network.bluetooth.state.BleLink) {
        if (!store.retireServerOwnership(link) { finishRadioHandshake(it, "server transfer retirement") }) return
        AppLogger.removeLink(link.endpoint, "SERVER", link.generation)
        if (!store.links.hasLiveRole(link.endpoint, BleLinkRole.CLIENT)) {
            link.serverDevice?.let { try { gattServer?.cancelConnection(it) } catch (_: SecurityException) {} }
        }
        cleanupEndpointIfUnowned(link.endpoint)
        if (!hasLiveSocket(link.endpoint)) {
            store.connectedEndpointIds.remove(link.endpoint)
            store.connectedEndpointNames.remove(link.endpoint)
            store.endpointNodeIds.remove(link.endpoint)
            store.connectionEstablishTime.remove(link.endpoint)
        }
        if (!hasReadyEndpoint(link.endpoint)) onDeviceDisconnected?.invoke(link.endpoint)
        sendSystemPulse()
        renewIdleGattServerIfNeeded()
    }

    private fun renewIdleGattServerIfNeeded() {
        if (!store.isNodeActive.get() || store.activeServerConnections.isNotEmpty() ||
            !store.serverIndications.hasUnresolved()) return
        // A missing indication callback cannot safely be attributed to a same-address replacement.
        // Renew only an idle server registration; CLIENT roles and other peers' sockets survive.
        AppLogger.d("BLE_MESH", "SERVER_REGISTRATION_RENEW unresolved indication; no server peers")
        serverRegistrationGeneration++
        try { gattServer?.close() } catch (_: Exception) {}
        gattServer = null
        serviceReady = false
        handler.removeCallbacks(startupTimeout)
        handler.postDelayed(startupTimeout, 5_000L)
        try { startGattServer() } catch (_: Exception) { failTransport(transportGeneration) }
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
            isUsableDirectNeighbor(endpoint)
        }

    private fun isUsableDirectNeighbor(endpoint: String): Boolean {
        val name = store.connectedEndpointNames[endpoint]
        return com.example.testresqmesh.core.network.bluetooth.state.BleDirectPeerPolicy.isUsable(
            hasReadyEndpoint(endpoint), name, nodeIdForEndpoint(endpoint), name?.let(::isDeviceBlocked) == true
        )
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
                com.example.testresqmesh.core.network.bluetooth.state.BleDirectPeerPolicy.capacityKey(
                    store.connectedEndpointNames[endpoint], nodeIdForEndpoint(endpoint), endpoint
                )
            }
            .toSet()
            .size
    }

    fun distinctReadyLinkCount(): Int {
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { isUsableDirectNeighbor(it) }
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
    fun completeServerIndication(ticket: com.example.testresqmesh.core.network.bluetooth.state.ServerIndicationLedger.Ticket, status: Int) =
        gattTransferExecutor.completeServer(ticket, status)
    fun startGattServer() { gattServerManager.startGattServer() }

    fun startL2capServer() { gattServerManager.startL2capServer() }

    fun handleL2capConnection(macAddress: String, socket: BluetoothSocket) =
        l2capTransport.attach(macAddress, socket)

    fun processBinaryPayload(endpointId: String, payloadBytes: ByteArray) {
        val capturedGeneration = transportGeneration
        val receivingLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpointId, it) }
        if (!isTransportGenerationCurrent(capturedGeneration) || !hasLiveSocket(endpointId)) {
            AppLogger.d("BLE_MESH", "Dropping payload from unowned endpoint $endpointId")
            return
        }
        try {
            val payload = kotlinx.serialization.protobuf.ProtoBuf.decodeFromByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payloadBytes)
            if (payload.type in setOf(ReliableMeshTransfers.PIECE, ReliableMeshTransfers.ACK)) {
                val peer = nodeIdForEndpoint(endpointId)?.uppercase() ?: return
                if (!hasReadyEndpoint(endpointId) || !store.links.isIdentityAdmitted(endpointId) ||
                    isDeviceBlocked(store.connectedEndpointNames[endpointId].orEmpty()) ||
                    peerTransferVersions[peer] != ReliableMeshTransfers.VERSION ||
                    payload.targetNodeId.isNotBlank() && !payload.targetNodeId.equals(myNodeId, true)) return
                reliableTransfers.receive(peer, payload)
                return
            }
            onDeviceLivenessChanged?.invoke(endpointId, true)
            if (payload.type != "PONG") heartbeatCoordinator.remove(endpointId)

            // Inbound central addresses are often unknown at ACL creation. A direct SYSTEM pulse is
            // the first reliable endpoint-to-stable-identity binding; reject it before the peer is
            // published or normal traffic is dispatched. Relayed packets never take this path.
            val isDirectIdentityPulse = payload.type == "SYSTEM" && payload.routePath.isEmpty() && payload.senderName.isNotEmpty()
            if (isDirectIdentityPulse && isDeviceBlocked(payload.senderName)) {
                AppLogger.d("BLE_MESH", "Identity gate rejected direct blocked peer ${payload.senderName} on $endpointId")
                val rejectedLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpointId, it) }
                handler.post {
                    if (!isTransportGenerationCurrent(capturedGeneration)) return@post
                    // The receiving socket can still be Unknown Node. Retire it by captured ownership,
                    // never by the unbound name, and never retire a replacement on the same endpoint.
                    if (store.links.ownsEndpoint(endpointId, rejectedLinks)) disconnectFromEndpoint(endpointId)
                    disconnectDirectIdentity(payload.senderName, "identity gate")
                }
                return
            }
            
            // Only auto-rename the physical socket if this is a direct message (not relayed).
            // Relayed payloads carry a non-empty routePath; renaming from those would map a remote
            // node onto a local socket and corrupt the routing table.
            if (isDirectIdentityPulse) {
                val claimedId = com.example.testresqmesh.core.network.bluetooth.DirectIdentityPolicy.validate(payload) {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                } ?: return
                receivingLinks.forEach { it.identityAdmitted = true }
                peerTransferVersions[claimedId] = payload.transferProtocol
                reliableTransfers.wake(claimedId, reconcile = false)
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
                        if (!isTransportGenerationCurrent(capturedGeneration) ||
                            !store.links.ownsEndpoint(endpointId, receivingLinks)) return@post
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

    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        cacheOutgoingMessageId(payloadBytes)
        val p = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(payloadBytes) }.getOrNull()
        val excludedPeer = excludeEndpointId?.let(::nodeIdForEndpoint)
        val visited = if (p != null && !p.isPrivate && p.type in setOf("MESSAGE", "CONVERSATION"))
            (p.routePath + p.senderName).mapNotNull(NodeIdentity::idOf).map { it.uppercase() }.toSet() else emptySet()
        val peers = (store.activeConnections.keys + store.activeServerConnections.keys).distinct()
            .filter { it != excludeEndpointId && hasReadyEndpoint(it) }.mapNotNull(::nodeIdForEndpoint)
            .map { it.uppercase() }.filter { it !in visited && !it.equals(excludedPeer, true) && endpointForPeer(it) != null }.toSet()
        val custody = p != null && excludeEndpointId != null && reliableTransfers.hasPendingCustody(p.id)
        val roster = if (custody) reliableTransfers.relayTargets(p!!.id, peers) else peers
        return BroadcastDispatchResult(roster.associateWith { peer ->
            if (custody && reliableTransfers.alreadyForwarded(p!!.id, peer)) TransportDispatchResult.ACCEPTED
            else if (custody && peerTransferVersions[peer] == ReliableMeshTransfers.VERSION) {
                reliableTransfers.offer(peer, payloadBytes, false, durableRelay = true)
            } else endpointForPeer(peer)?.let {
                if (custody) sendLegacyRelay(peer, it, payloadBytes) else sendDirectPayload(it, payloadBytes)
            } ?: TransportDispatchResult.REJECTED_NOT_READY
        })
    }

    private fun sendLegacyRelay(peer: String, endpoint: String, bytes: ByteArray): TransportDispatchResult {
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull()
            ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        if (!reliableTransfers.hasPendingCustody(payload.id)) return sendDirectPayload(endpoint, bytes)
        val key = "$peer:${payload.id}"
        if (legacyRelayFrames.putIfAbsent(key, endpoint) != null) return TransportDispatchResult.ACCEPTED
        return sendDirectPayload(endpoint, bytes).also { if (!it.accepted) legacyRelayFrames.remove(key, endpoint) }
    }

    /** SOS/control callers may overtake a queued low-priority attachment frame. */
    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult {
        cacheOutgoingMessageId(payloadBytes)
        val targets = mutableSetOf<String>()
        targets.addAll(store.activeConnections.keys)
        targets.addAll(store.activeServerConnections.keys)
        targets.remove(excludeEndpointId)
        return BroadcastDispatchResult(targets.associateWith { sendPriorityPayload(it, payloadBytes) })
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
            it.state == BleLinkState.READY && !it.gattQuarantined && it.currentOperation == null && store.activeConnections.containsKey(endpoint)
        } ?: store.links.current(endpoint, BleLinkRole.SERVER)?.takeIf {
            it.state == BleLinkState.READY && !it.gattQuarantined && store.activeServerConnections.containsKey(endpoint)
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
        fun checkAck() {
            if (heartbeatCoordinator.pending(endpoint) != sent) return
            val current = store.links.current(endpoint, sent.role)
            if (current == null || current.generation != sent.generation) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (store.connectionInteractionTimes[endpoint]?.let { it > sent.sentAt } == true) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (store.activeL2capSockets.containsKey(endpoint)) {
                heartbeatCoordinator.remove(endpoint, sent)
                return
            }
            if (com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy.hasRecentOutboundProgress(
                    current.lastAcknowledgedWriteAt, System.currentTimeMillis())) {
                handler.postDelayed({ checkAck() }, HEARTBEAT_ACK_TIMEOUT_MS)
                return
            }
            if (sent.expired(System.currentTimeMillis(), HEARTBEAT_ACK_TIMEOUT_MS)) {
                heartbeatCoordinator.remove(endpoint, sent)
                AppLogger.d("BLE_MESH", "Link ${sent.generation} $endpoint: GATT heartbeat ACK timed out; retiring silent roles")
                store.activeConnections[endpoint]?.let { forceGattDisconnect(endpoint, it) }
                store.activeServerConnections[endpoint]?.let { gattServer?.cancelConnection(it) }
            }
        }
        handler.postDelayed({ checkAck() }, HEARTBEAT_ACK_TIMEOUT_MS)
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
        return dispatchPayload(targetEndpointId, payloadBytes, true)
    }

    fun sendDirectPayload(targetMacAddress: String, payloadBytes: ByteArray): TransportDispatchResult {
        return dispatchPayload(targetMacAddress, payloadBytes, isControlPayload(payloadBytes))
    }

    private fun dispatchPayload(endpoint: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        if (!BluetoothAdapter.checkBluetoothAddress(endpoint)) return TransportDispatchResult.REJECTED_INVALID_ENDPOINT
        val payload = runCatching { ProtoBuf.decodeFromByteArray<MeshPayload>(bytes) }.getOrNull()
            ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        val peer = nodeIdForEndpoint(endpoint)?.uppercase()
        val selected = if (peer != null && DirectSendPolicy.usesPeerEndpoint(payload.type)) endpointForPeer(peer) ?: endpoint else endpoint
        if (!hasReadyEndpoint(selected)) return TransportDispatchResult.REJECTED_NOT_READY
        if (payload.type in setOf("MESSAGE", "CONVERSATION") && !store.links.isIdentityAdmitted(selected)) {
            return TransportDispatchResult.REJECTED_NOT_READY
        }
        cacheOutgoingMessageId(bytes)
        return if (peer != null && peerTransferVersions[peer] == ReliableMeshTransfers.VERSION && DirectSendPolicy.usesPieces(payload.type, bytes.size)) {
            reliableTransfers.offer(peer, bytes, priority, durableRelay = true)
        } else sendRawPayload(selected, bytes, priority)
    }

    /** Only complete wire frames enter a channel; transfer pieces bypass recursive fragmentation. */
    private fun sendRawPayload(endpoint: String, bytes: ByteArray, priority: Boolean): TransportDispatchResult {
        if (!com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy.fitsSingleTransfer(bytes.size + Int.SIZE_BYTES, priority)) {
            return TransportDispatchResult.REJECTED_INVALID_FRAME
        }
        if (!hasReadyEndpoint(endpoint)) return TransportDispatchResult.REJECTED_NOT_READY
        // The failed socket's accepted frames retain ownership before new ordinary work is admitted.
        if (!priority && retainedFallback.hasPending(endpoint)) return TransportDispatchResult.REJECTED_QUEUE_FULL
        val l2capResult = l2capTransport.send(endpoint, bytes, priority)
        if (l2capResult != TransportDispatchResult.REJECTED_NOT_READY) {
            if (l2capResult.accepted) reportFrame(endpoint, bytes, OutboundFrameEvent.Stage.QUEUED, "L2CAP")
            return l2capResult
        }
        val frame = MeshFrameCodec.encode(bytes) ?: return TransportDispatchResult.REJECTED_INVALID_FRAME
        if (!transferCoordinator.enqueue(endpoint, GattTransfer(frame), priority)) return TransportDispatchResult.REJECTED_QUEUE_FULL
        reportFrame(endpoint, bytes, OutboundFrameEvent.Stage.QUEUED, "GATT")
        processNextPayload(endpoint)
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
            store.links.retire(link) { finishRadioHandshake(it, "forced client retirement") }
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
            if (!hasReadyEndpoint(macAddress)) onDeviceDisconnected?.invoke(macAddress)
            sendSystemPulse()
        }
    }

    private fun hasUsableL2cap(endpoint: String): Boolean =
        l2capTransport.isUsable(endpoint) && hasLiveSocket(endpoint)

    /**
     * Hands queued complete frames to L2CAP only after the callback-owned GATT frame finishes.
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
        var deferred = false
        promoted.forEach { transfer ->
            val result = if (deferred) TransportDispatchResult.REJECTED_QUEUE_FULL
                else l2capTransport.send(endpoint, transfer.payloadBytes(), transfer.priority)
            if (!result.accepted) {
                deferred = true
                // Keep deferred promotion work until the socket writer frees capacity. Never lose
                // an already accepted frame or create a second GATT lane beside a congested socket.
                store.pendingQueues[endpoint]?.addLast(transfer)
                AppLogger.d("BLE_MESH", "L2CAP promotion deferred on $endpoint: $result")
            }
        }
    }

    fun disconnectFromEndpoint(endpointId: String) {
        listOf(
            BleLinkRole.CLIENT,
            BleLinkRole.SERVER
        ).forEach { role ->
            store.links.current(endpointId, role)?.let { link ->
                store.links.retire(link) { finishRadioHandshake(it, "endpoint retirement") }
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

        l2capTransport.disconnect(endpointId)

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
        handler.post {
            if (store.isNodeActive.get()) {
                sessionLifecycle.peers(distinctReadyLinkCount())
                radioController.startScanning()
                peerAdmissionController.recover()
            }
        }
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
        handler.post {
            if (!sessionLifecycle.requested) return@post
            sessionLifecycle.reconcile(bluetoothAvailability())
            if (!serviceReady) return@post
            radioController.reconcileHandshakeOwners(store.links::isSetupOwner)
            peerAdmissionController.drainCandidates()
            radioController.rescan()
        }
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
