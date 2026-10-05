package com.example.testresqmesh.data.repository

import com.example.testresqmesh.data.repository.mesh.MeshBlockCoordinator
import com.example.testresqmesh.data.repository.mesh.MeshConversationMedia
import com.example.testresqmesh.data.repository.mesh.MeshInboundMessages
import com.example.testresqmesh.data.repository.mesh.MeshOutboundDelivery
import com.example.testresqmesh.data.repository.mesh.MeshPeerCoordinator
import com.example.testresqmesh.data.repository.mesh.SosReplyCapability
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException

class MeshRepository(
    private val networkManager: MeshNetworkGateway,
    private val messageStore: MessageStore,
    private val blockStore: BlockRelationshipStore,
    private val publicKeys: PeerPublicKeyDirectory,
    private val repositoryScope: CoroutineScope,
    private val readyPeerEvents: MeshReadyPeerEvents,
    private val peerNameStore: PeerNameStore,
    private val sosRepository: SosRepository? = null
) {

    private val _connectionStatus = MutableStateFlow("Ready to deploy Mesh Node.")
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _transportState = MutableStateFlow(com.example.testresqmesh.core.network.bluetooth.MeshTransportState.OFFLINE)
    val transportState = _transportState.asStateFlow()
    fun reconcileTransport() = networkManager.reconcileTransport()

    private val _currentChannelId = MutableStateFlow("1")
    val currentChannelId: StateFlow<String> = _currentChannelId.asStateFlow()

    fun setChannel(channelId: String) = media.setChannel(channelId)

    private val _connectedDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices.asStateFlow()
    private fun readyConnectedDevices(): List<ConnectedDevice> = peers.readyConnectedDevices()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _incomingSosAlert = MutableStateFlow<ChatMessage?>(null)
    val incomingSosAlert: StateFlow<ChatMessage?> = _incomingSosAlert.asStateFlow()

    val incomingVoiceMessage = kotlinx.coroutines.flow.MutableSharedFlow<ChatMessage>(extraBufferCapacity = 10)
    private val _publicSendFeedback = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    val publicSendFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _publicSendFeedback
    val incomingLiveAudioChunk = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 100)

    fun clearSosAlert() = media.clearSosAlert()

    private val meshRouter = MeshRouter()
    val knownNodes = meshRouter.knownNodes
    val topology = meshRouter.topology

    val publicMessages = messageStore.publicMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())
    val allPublicMessages = messageStore.allPublicMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val privateMessages = messageStore.privateMessages
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    private val recentPeerNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerNames = combine(peerNameStore.names, recentPeerNames) { saved, recent -> saved + recent }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())
    private val observedPeerNames = Channel<String>(64, BufferOverflow.DROP_OLDEST)

    private fun recordPeerName(name: String, fromReadyLink: Boolean = false) = peers.recordPeerName(name, fromReadyLink)

    private fun currentPeerName(name: String): String = peers.currentPeerName(name)

    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private var myNodeName: String = ""
    private val sosReplies = sosRepository?.let { SosReplyCapability(it::canReply) }
    val blockRelationships = blockStore.relationships
    private val blocks: MeshBlockCoordinator = createBlockCoordinator()
    private val outbound: MeshOutboundDelivery = createOutboundDelivery()

    private val peers: MeshPeerCoordinator = createPeerCoordinator()

    private val inbound: MeshInboundMessages = createInboundMessages()

    private val media: MeshConversationMedia = createConversationMedia()

    init {
        setupCallbacks()
        repositoryScope.launch {
            for (name in observedPeerNames) {
                try {
                    peerNameStore.observeFullName(name)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AppLogger.d("PeerNames", "Unable to persist a peer display name")
                }
            }
        }
        meshRouter.startTopologyCleanup(repositoryScope, { myNodeName }, { readyConnectedDevices() })
        outbound.startWorker()
    }

    /**
     * Identity comparison for two links. Falls back to the stable node ID so a provisional socket
     * (whose name is still a placeholder, and which therefore matches nothing by name) is still
     * recognised as the same peer.
     */


    private fun setupCallbacks() {
        bindMeshNetworkEvents(
            networkManager,
            MeshNetworkCallbacks(
                onStatusChanged = ::onNetworkStatusChanged,
                onOutboundFrame = ::onNetworkOutboundFrame,
                onTransportStateChanged = ::onNetworkTransportStateChanged,
                onDeviceConnected = ::onNetworkDeviceConnected,
                onDeviceDisconnected = ::onNetworkDeviceDisconnected,
                onDeviceLivenessChanged = ::onNetworkDeviceLivenessChanged,
                onPublicKeyReceived = ::onNetworkPublicKeyReceived,
                onRoutingTableReceived = ::onNetworkRoutingTableReceived,
                onDeviceBlocked = ::onNetworkDeviceBlocked,
                onDeviceUnblocked = ::onNetworkDeviceUnblocked,
                onBlockRequest = ::onNetworkBlockRequest,
                onBlockAck = ::onNetworkBlockAck,
                checkRouteExists = ::networkRouteExists,
                canRetireForBridge = ::canNetworkRetireForBridge,
                onDeviceScanned = ::onNetworkDeviceScanned,
                onDeviceScanRemoved = ::onNetworkDeviceScanRemoved,
                onSosCancelled = ::onNetworkSosCancelled,
                onConversationMessage = ::onNetworkConversationMessage,
                stpNeighborsProvider = ::networkSpanningTreeNeighbors,
                onLiveAudioChunk = ::onNetworkLiveAudioChunk,
                onMessageReceived = ::onNetworkMessageReceived,
                onMessageDelivered = ::onNetworkMessageDelivered,
                onMessageSeen = ::onNetworkMessageSeen
            )
        )
    }

    private fun onNetworkStatusChanged(status: String): Unit = peers.onNetworkStatusChanged(status)

    private fun onNetworkOutboundFrame(event: OutboundFrameEvent): Unit = outbound.onNetworkOutboundFrame(event)

    private fun onNetworkTransportStateChanged(state: MeshTransportState): Unit = peers.onNetworkTransportStateChanged(state)

    private fun onNetworkDeviceConnected(device: ConnectedDevice): Unit = peers.onNetworkDeviceConnected(device)

    private fun onNetworkDeviceDisconnected(endpointId: String): Unit = peers.onNetworkDeviceDisconnected(endpointId)

    private fun onNetworkDeviceLivenessChanged(endpointId: String, responsive: Boolean): Unit = peers.onNetworkDeviceLivenessChanged(endpointId, responsive)

    private fun onNetworkPublicKeyReceived(senderName: String, senderNodeId: String, key: String): Unit = peers.onNetworkPublicKeyReceived(senderName, senderNodeId, key)

    private fun onNetworkRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>, topologySequence: Long): Unit = peers.onNetworkRoutingTableReceived(senderName, senderNodeId, connectedNodes, connectedNodeIds, topologySequence)

    private fun onNetworkDeviceBlocked(senderName: String): Unit = blocks.onNetworkDeviceBlocked(senderName)

    private fun onNetworkDeviceUnblocked(senderName: String): Unit = blocks.onNetworkDeviceUnblocked(senderName)

    private fun onNetworkBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope): Unit = blocks.onNetworkBlockRequest(endpointId, payload, envelope)

    private fun onNetworkBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope): Unit = blocks.onNetworkBlockAck(endpointId, payload, envelope)

    private fun networkRouteExists(targetName: String): Boolean = peers.networkRouteExists(targetName)

    private fun canNetworkRetireForBridge(endpoint: String): Boolean = peers.canNetworkRetireForBridge(endpoint)

    private fun onNetworkDeviceScanned(event: ScanEvent): Unit = peers.onNetworkDeviceScanned(event)

    private fun onNetworkDeviceScanRemoved(id: String): Unit = peers.onNetworkDeviceScanRemoved(id)

    private fun onNetworkSosCancelled(): Unit = media.onNetworkSosCancelled()

    private fun onNetworkConversationMessage(endpoint: String, payload: MeshPayload): Unit = inbound.onNetworkConversationMessage(endpoint, payload)

    private fun networkSpanningTreeNeighbors(): Set<String> = peers.networkSpanningTreeNeighbors()

    private fun onNetworkLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray): Unit = media.onNetworkLiveAudioChunk(sender, channelId, chunk)

    private fun onNetworkMessageReceived(endpointId: String, msgId: String, sender: String, text: String, isPrivate: Boolean, isSystem: Boolean, img: String?, audio: String?, lat: Double?, lng: Double?, medium: String, routePath: List<String>, channelId: String): Unit = inbound.onNetworkMessageReceived(endpointId, msgId, sender, text, isPrivate, isSystem, img, audio, lat, lng, medium, routePath, channelId)

    private fun onNetworkMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>): Unit = outbound.onNetworkMessageDelivered(msgId, readerName, returnRoute)

    private fun onNetworkMessageSeen(msgId: String, readerName: String): Unit = outbound.onNetworkMessageSeen(msgId, readerName)


    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null) = inbound.broadcastSeenReceipt(messageId, isPrivate, targetName)

    fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        myNodeName = NodeIdentity.qualifiedName(customName, nodeTag, nodeId)
        networkManager.myDeviceName = myNodeName
        networkManager.myNodeId = nodeId
        synchronizeBlockRelationships()
        networkManager.startMeshNode(teamKey)
        resumePendingBlockRequests()
        _isOnline.value = true
    }

    fun stopNode() {
        networkManager.stopMeshNode()
        _isOnline.value = false
        _connectedDevices.value = emptyList()
        readyPeerEvents.update(emptyList())
        _scannedDevices.value = emptyList()
    }

    fun disconnectDevice(endpointId: String) {
        networkManager.disconnectFromEndpoint(endpointId)
    }

    private val _blockedDeviceNames = MutableStateFlow<Set<String>>(emptySet())
    val blockedDeviceNames: StateFlow<Set<String>> = _blockedDeviceNames.asStateFlow()



    private fun synchronizeBlockRelationships() = blocks.synchronizeBlockRelationships()

    private fun resumePendingBlockRequests() = blocks.resumePendingBlockRequests()

    fun blockDevice(deviceName: String) = blocks.blockDevice(deviceName)

    fun unblockDevice(deviceName: String) = blocks.unblockDevice(deviceName)


















    private fun scheduleOutboxFlush() = outbound.scheduleOutboxFlush()







    @Synchronized
    fun broadcastLiveAudioChunk(chunk: ByteArray) = media.broadcastLiveAudioChunk(chunk)

    fun forceConnect(endpointId: String, endpointName: String) {
        networkManager.forceConnectToDevice(endpointId, endpointName)
    }

    fun rescan() {
        networkManager.rescan()
    }

    fun sendPublicMessage(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false,
        conversationKind: String = "COMMUNITY", channelId: String = "", sosId: String = ""): String = outbound.sendPublicMessage(text, imageBase64, audioBase64, locationLat, locationLng, isSOS, isSOSCancel, conversationKind, channelId, sosId)



    fun deleteConversationWith(peerName: String) = inbound.deleteConversationWith(peerName)

    fun hasPendingPublicKeyChange(peerName: String): Boolean = inbound.hasPendingPublicKeyChange(peerName)

    fun acceptPendingPublicKeyChange(peerName: String): Boolean = inbound.acceptPendingPublicKeyChange(peerName)

    fun rejectPendingPublicKeyChange(peerName: String): Boolean = inbound.rejectPendingPublicKeyChange(peerName)

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null): Boolean = outbound.sendPrivateMessage(targetName, text, imageBase64, audioBase64, locationLat, locationLng)

    private fun createPeerCoordinator() = MeshPeerCoordinator(
        networkManager,
        publicKeys,
        readyPeerEvents,
        meshRouter,
        _connectionStatus,
        _transportState,
        _connectedDevices,
        _scannedDevices,
        recentPeerNames,
        peerNames,
        observedPeerNames,
        { myNodeName },
        ::scheduleOutboxFlush
    )

    private fun createOutboundDelivery() = MeshOutboundDelivery(
        networkManager,
        messageStore,
        publicKeys,
        repositoryScope,
        sosReplies,
        meshRouter,
        _currentChannelId,
        _incomingSosAlert,
        _publicSendFeedback,
        { myNodeName },
        ::readyConnectedDevices,
        ::currentPeerName
    )

    private fun createBlockCoordinator() = MeshBlockCoordinator(
        networkManager,
        blockStore,
        publicKeys,
        repositoryScope,
        meshRouter,
        { myNodeName },
        { _blockedDeviceNames },
        ::readyConnectedDevices
    )

    private fun createInboundMessages() = MeshInboundMessages(
        networkManager,
        messageStore,
        publicKeys,
        repositoryScope,
        sosReplies,
        meshRouter,
        _currentChannelId,
        _connectedDevices,
        _incomingSosAlert,
        incomingVoiceMessage,
        { myNodeName },
        ::readyConnectedDevices,
        ::recordPeerName,
        outbound.outboxMutex
    )

    private fun createConversationMedia() = MeshConversationMedia(
        networkManager,
        repositoryScope,
        meshRouter,
        _currentChannelId,
        _connectedDevices,
        _incomingSosAlert,
        incomingLiveAudioChunk,
        { myNodeName },
        ::readyConnectedDevices
    )
}
