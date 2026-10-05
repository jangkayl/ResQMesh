package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState

typealias MessageReceivedCallback = (
    String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>, String
) -> Unit

/** Repository-facing transport contract. It deliberately exposes no Android Bluetooth classes. */
interface MeshNetworkGateway {
    val reportsOutboundProgress: Boolean get() = false
    var onOutboundFrame: ((OutboundFrameEvent) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    fun hasPendingTransfer(messageId: String): Boolean = false
    fun hasPendingCustody(payloadId: String): Boolean = false
    fun markPayloadStored(messageId: String) {}
    fun preferredEndpointForPeer(nodeId: String): String? = null
    var onConversationMessage: ((String, MeshPayload) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var onSosPacket: ((String, MeshPayload) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var myDeviceName: String
    var myNodeId: String
    var onDeviceConnected: ((ConnectedDevice) -> Unit)?
    var onDeviceDisconnected: ((String) -> Unit)?
    var onDeviceLivenessChanged: ((String, Boolean) -> Unit)?
    var onDeviceScanned: ((ScanEvent) -> Unit)?
    var onDeviceScanRemoved: ((String) -> Unit)?
    var onMessageReceived: MessageReceivedCallback?
    var onMessageSeen: ((String, String) -> Unit)?
    var onLiveAudioChunk: ((String, String, ByteArray) -> Unit)?
    var onMessageDelivered: ((String, String, List<String>) -> Unit)?
    var onPublicKeyReceived: ((String, String, String) -> Unit)?
    var onRoutingTableReceived: ((String, String, List<String>, List<String>, Long) -> Unit)?
    var onSosCancelled: (() -> Unit)?
    var onStatusChanged: ((String) -> Unit)?
    var onTransportStateChanged: ((MeshTransportState) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var canRetireForBridge: ((String) -> Boolean)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}
    var onDeviceBlocked: ((String) -> Unit)?
    var onDeviceUnblocked: ((String) -> Unit)?
    var onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)?
    var onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)?
    var onDomainEvent: ((String, MeshPayload) -> Unit)?
    var onEventSyncRequest: ((String, MeshPayload) -> Unit)?
    var onEventSyncResponse: ((String, MeshPayload) -> Unit)?
    var checkRouteExists: ((String) -> Boolean)?
    var stpNeighborsProvider: (() -> Set<String>)?

    fun startMeshNode(teamKey: String)
    fun stopMeshNode()
    fun hasReadyEndpoint(endpointId: String): Boolean
    fun currentMeshTtl(): Int
    fun hasLiveSocket(endpointId: String): Boolean
    fun hasReadyLinkToIdentity(peerName: String): Boolean
    fun linkEstablishedAt(endpointId: String): Long
    fun disconnectFromEndpoint(endpointId: String)
    fun blockDevice(deviceName: String)
    fun unblockDevice(deviceName: String)
    fun denyDirectIdentity(deviceName: String)
    fun releaseDirectIdentity(deviceName: String)
    fun disconnectDirectIdentity(deviceName: String, reason: String)
    fun isDeviceBlocked(deviceName: String): Boolean
    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult
    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null): BroadcastDispatchResult
    fun sendDirectPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult
    fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult
    fun showPrivateMessageNotification(sender: String, text: String) {}
    fun wakePrivateReceipts() {}
    fun broadcastSeenReceipt(
        messageId: String,
        isPrivate: Boolean,
        targetId: String? = null,
        directedReturnRoute: List<String> = emptyList()
    )
    fun broadcastDeliveredReceipt(
        messageId: String,
        isPrivate: Boolean,
        targetId: String? = null,
        directedReturnRoute: List<String> = emptyList()
    )
    fun forceConnectToDevice(endpointId: String, endpointName: String)
    fun rescan()
    fun reconcileTransport() {}
}
