package com.example.testresqmesh.core.network



/** Keeps NativeBleManager available to Android entry points while repositories depend on the contract. */
class NativeBleGateway(private val manager: NativeBleManager) : MeshNetworkGateway {
    override val reportsOutboundProgress = true
    override var onOutboundFrame by manager::onOutboundFrame
    override fun hasPendingTransfer(messageId: String) = manager.hasPendingTransfer(messageId)
    override fun hasPendingCustody(payloadId: String) = manager.hasPendingCustody(payloadId)
    override fun markPayloadStored(messageId: String) = manager.markPayloadStored(messageId)
    override fun preferredEndpointForPeer(nodeId: String) = manager.preferredEndpointForPeer(nodeId)
    override var onConversationMessage by manager::onConversationMessage
    override var onSosPacket by manager::onSosPacket
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
    override var onTransportStateChanged by manager::onTransportStateChanged
    override var canRetireForBridge by manager::canRetireForBridge
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
    override fun showPrivateMessageNotification(sender: String, text: String) = manager.showPrivateMessageNotification(sender, text)
    override fun wakePrivateReceipts() = manager.wakePrivateReceipts()
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
    override fun reconcileTransport() = manager.reconcileTransport()
}
