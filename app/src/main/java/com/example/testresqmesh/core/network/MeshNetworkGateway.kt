package com.example.testresqmesh.core.network

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScanEvent

typealias MessageReceivedCallback = (
    String, String, String, String, Boolean, Boolean, String?, String?, Double?, Double?, String, List<String>, String
) -> Unit

/** Repository-facing transport contract. It deliberately exposes no Android Bluetooth classes. */
interface MeshNetworkGateway {
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
    fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null)
    fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String? = null)
    fun sendDirectPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult
    fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray): TransportDispatchResult
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
}

/** Keeps NativeBleManager available to Android entry points while repositories depend on the contract. */
class NativeBleGateway(private val manager: NativeBleManager) : MeshNetworkGateway {
    override var myDeviceName: String
        get() = manager.myDeviceName
        set(value) { manager.myDeviceName = value }
    override var myNodeId: String
        get() = manager.myNodeId
        set(value) { manager.myNodeId = value }
    override var onDeviceConnected by manager::onDeviceConnected
    override var onDeviceDisconnected by manager::onDeviceDisconnected
    override var onDeviceLivenessChanged by manager::onDeviceLivenessChanged
    override var onDeviceScanned by manager::onDeviceScanned
    override var onDeviceScanRemoved by manager::onDeviceScanRemoved
    override var onMessageReceived by manager::onMessageReceived
    override var onMessageSeen by manager::onMessageSeen
    override var onLiveAudioChunk by manager::onLiveAudioChunk
    override var onMessageDelivered by manager::onMessageDelivered
    override var onPublicKeyReceived by manager::onPublicKeyReceived
    override var onRoutingTableReceived by manager::onRoutingTableReceived
    override var onSosCancelled by manager::onSosCancelled
    override var onStatusChanged by manager::onStatusChanged
    override var onDeviceBlocked by manager::onDeviceBlocked
    override var onDeviceUnblocked by manager::onDeviceUnblocked
    override var onBlockRequest by manager::onBlockRequest
    override var onBlockAck by manager::onBlockAck
    override var onDomainEvent by manager::onDomainEvent
    override var onEventSyncRequest by manager::onEventSyncRequest
    override var onEventSyncResponse by manager::onEventSyncResponse
    override var checkRouteExists by manager::checkRouteExists
    override var stpNeighborsProvider by manager::stpNeighborsProvider

    override fun startMeshNode(teamKey: String) = manager.startMeshNode(teamKey)
    override fun stopMeshNode() = manager.stopMeshNode()
    override fun hasReadyEndpoint(endpointId: String) = manager.hasReadyEndpoint(endpointId)
    override fun currentMeshTtl() = manager.getMeshProfileTtl()
    override fun hasLiveSocket(endpointId: String) = manager.hasLiveSocket(endpointId)
    override fun hasReadyLinkToIdentity(peerName: String) = manager.hasReadyLinkToIdentity(peerName)
    override fun linkEstablishedAt(endpointId: String) = manager.linkEstablishedAt(endpointId)
    override fun disconnectFromEndpoint(endpointId: String) = manager.disconnectFromEndpoint(endpointId)
    override fun blockDevice(deviceName: String) = manager.blockDevice(deviceName)
    override fun unblockDevice(deviceName: String) = manager.unblockDevice(deviceName)
    override fun denyDirectIdentity(deviceName: String) = manager.denyDirectIdentity(deviceName)
    override fun releaseDirectIdentity(deviceName: String) = manager.releaseDirectIdentity(deviceName)
    override fun disconnectDirectIdentity(deviceName: String, reason: String) = manager.disconnectDirectIdentity(deviceName, reason)
    override fun isDeviceBlocked(deviceName: String) = manager.isDeviceBlocked(deviceName)
    override fun broadcastPayload(payloadBytes: ByteArray, excludeEndpointId: String?) =
        manager.broadcastPayload(payloadBytes, excludeEndpointId)
    override fun broadcastPriorityPayload(payloadBytes: ByteArray, excludeEndpointId: String?) =
        manager.broadcastPriorityPayload(payloadBytes, excludeEndpointId)
    override fun sendDirectPayload(targetEndpointId: String, payloadBytes: ByteArray) =
        manager.sendDirectPayload(targetEndpointId, payloadBytes)
    override fun sendPriorityPayload(targetEndpointId: String, payloadBytes: ByteArray) =
        manager.sendPriorityPayload(targetEndpointId, payloadBytes)
    override fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetId: String?, directedReturnRoute: List<String>) =
        manager.broadcastSeenReceipt(messageId, isPrivate, targetId, directedReturnRoute)
    override fun broadcastDeliveredReceipt(
        messageId: String,
        isPrivate: Boolean,
        targetId: String?,
        directedReturnRoute: List<String>
    ) = manager.broadcastDeliveredReceipt(messageId, isPrivate, targetId, directedReturnRoute)
    override fun forceConnectToDevice(endpointId: String, endpointName: String) =
        manager.forceConnectToDevice(endpointId, endpointName)
    override fun rescan() = manager.rescan()
}
