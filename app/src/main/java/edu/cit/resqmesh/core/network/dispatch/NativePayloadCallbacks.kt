package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.BlockControlEnvelope
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.NativePayloadEvents
import edu.cit.resqmesh.core.network.PayloadDispatcherCallback
import edu.cit.resqmesh.core.network.dedup.ExpiringIdCache
import edu.cit.resqmesh.core.network.TransportDispatchResult

/** Synchronous payload-to-application adapter. Reads current listeners and identity at invocation. */
internal class NativePayloadCallbacks(
    private val events: NativePayloadEvents,
    private val seenIds: () -> ExpiringIdCache,
    private val endpointForPeer: (String) -> String?,
    private val pendingCustody: (String) -> Boolean,
    private val forwardPrivate: (String, ByteArray) -> TransportDispatchResult,
    private val direct: (String, ByteArray) -> TransportDispatchResult,
    private val priority: (String, ByteArray) -> TransportDispatchResult,
    private val receipt: (MeshPayload) -> Unit,
    private val gatt: (String, ByteArray) -> Unit,
    private val heartbeatAck: (String, String) -> Unit,
    private val broadcast: (ByteArray, String?) -> Unit,
    private val disconnect: (String) -> Unit,
    private val privateNotification: (String, String) -> Unit,
    private val sosNotification: (String, String) -> Unit,
    private val admittedNodeId: (String) -> String? = { null }
) : PayloadDispatcherCallback {
    override fun onConversationMessage(endpointId: String, payload: MeshPayload) {
        events.onConversationMessage?.invoke(endpointId, payload)
    }
    override fun onSosPacket(endpointId: String, payload: MeshPayload) {
        events.onSosPacket?.invoke(endpointId, payload)
    }
    override fun getMyDeviceName() = events.myDeviceName
    override fun getMyNodeId() = events.myNodeId
    override fun getSeenIdCache() = seenIds()
    override fun getEndpointMedium(endpointId: String) = "Persistent BLE Mesh"
    override fun getConnectedEndpointIdByName(name: String) = NodeIdentity.idOf(name)?.let(endpointForPeer)
    override fun getConnectedEndpointIdByNodeId(nodeId: String) = endpointForPeer(nodeId)
    override fun hasPendingCustody(payloadId: String) = pendingCustody(payloadId)
    override fun forwardPrivatePayload(nodeId: String, payload: ByteArray) = forwardPrivate(nodeId, payload)

    override fun getAdmittedNodeId(endpointId: String) = admittedNodeId(endpointId)

    override fun getStpNeighbors(): Set<String> {
        return events.stpNeighborsProvider?.invoke() ?: emptySet()
    }
    override fun sendDirectPayload(endpointId: String, payload: ByteArray) = direct(endpointId, payload)
    override fun sendPriorityPayload(endpointId: String, payload: ByteArray) = priority(endpointId, payload)
    override fun sendPrivateReceipt(payload: MeshPayload) = receipt(payload)
    override fun sendGattPayload(endpointId: String, payload: ByteArray) {
        gatt(endpointId, payload)
    }
    override fun onHeartbeatAck(endpointId: String, challengeId: String) = heartbeatAck(endpointId, challengeId)
    override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) { broadcast(payload, excludeEndpointId) }
    override fun onMessageSeen(msgId: String, readerName: String) { events.onMessageSeen?.invoke(msgId, readerName) }
    override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) { events.onMessageDelivered?.invoke(msgId, readerName, returnRoute) }
    override fun onPublicKeyReceived(senderName: String, senderNodeId: String, key: String) { events.onPublicKeyReceived?.invoke(senderName, senderNodeId, key) }
    override fun onRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>, topologySequence: Long) {
        events.onRoutingTableReceived?.invoke(senderName, senderNodeId, connectedNodes, connectedNodeIds, topologySequence)
    }
    override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String) {
        events.onMessageReceived?.invoke(endpointId, msgId, senderName, text, isPrivate, isSystem, imageBase64, audioBase64, locationLat, locationLng, medium, routePath, channelId)
    }
    override fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray) {
        events.onLiveAudioChunk?.invoke(sender, channelId, chunk)
    }
    override fun onDeviceNameSync(endpointId: String, realName: String) {
        // Deprecated: We now handle this safely in processBinaryPayload
        // by enforcing routePath.isEmpty() to prevent relayed pulses from corrupting the routing table.
    }
    override fun onDeviceGoodbye(endpointId: String) {
        AppLogger.d("BLE_MESH", "Received GOODBYE packet from $endpointId. Disconnecting instantly.")
        disconnect(endpointId)
    }
    override fun onSosCancelled() { events.onSosCancelled?.invoke() }
    override fun onBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {
        events.onBlockRequest?.invoke(endpointId, payload, envelope)
    }
    override fun onBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {
        events.onBlockAck?.invoke(endpointId, payload, envelope)
    }
    override fun onLegacyBlockControl(payloadType: String, senderName: String) {
        AppLogger.d("BLE_MESH", "Ignoring legacy $payloadType control from $senderName")
    }
    override fun showNotification(sender: String, text: String) { privateNotification(sender, text) }
    override fun showSosEmergencyNotification(sender: String, text: String) {
        if (!edu.cit.resqmesh.MainActivity.isAppInForeground) {
            sosNotification(sender, text)
        }
    }
    override fun onDomainEvent(endpointId: String, payload: MeshPayload) {
        events.onDomainEvent?.invoke(endpointId, payload)
    }
    override fun onEventSyncRequest(endpointId: String, payload: MeshPayload) {
        events.onEventSyncRequest?.invoke(endpointId, payload)
    }
    override fun onEventSyncResponse(endpointId: String, payload: MeshPayload) {
        events.onEventSyncResponse?.invoke(endpointId, payload)
    }
}
