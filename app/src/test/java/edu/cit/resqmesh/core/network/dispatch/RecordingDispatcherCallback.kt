package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.BlockControlEnvelope
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcherCallback
import edu.cit.resqmesh.core.network.TransportDispatchResult

/** Minimal [PayloadDispatcherCallback] that records the outcomes dispatcher tests assert on. */
internal class RecordingDispatcherCallback(
    val myName: String = "Relay [TAG]#B001",
    val myId: String = "B001"
) : PayloadDispatcherCallback {
    val seen = mutableSetOf<String>()
    val admitted = mutableMapOf<String, String>()
    val goodbyes = mutableListOf<String>()
    val broadcasts = mutableListOf<Pair<ByteArray, String?>>()
    val received = mutableListOf<String>()
    val sosPackets = mutableListOf<MeshPayload>()
    val conversations = mutableListOf<MeshPayload>()
    val routingTables = mutableListOf<String>()

    override fun onConversationMessage(endpointId: String, payload: MeshPayload) { conversations += payload }
    override fun onSosPacket(endpointId: String, payload: MeshPayload) { sosPackets += payload }
    override fun getMyDeviceName() = myName
    override fun getMyNodeId() = myId
    override fun getSeenMessageIds(): MutableSet<String> = seen
    override fun getAdmittedNodeId(endpointId: String) = admitted[endpointId]
    override fun getEndpointMedium(endpointId: String) = "BLE"
    override fun getConnectedEndpointIdByName(name: String): String? = null
    override fun getConnectedEndpointIdByNodeId(nodeId: String): String? = null
    override fun getStpNeighbors(): Set<String> = emptySet()
    override fun sendDirectPayload(endpointId: String, payload: ByteArray) = TransportDispatchResult.ACCEPTED
    override fun sendPriorityPayload(endpointId: String, payload: ByteArray) = TransportDispatchResult.ACCEPTED
    override fun sendGattPayload(endpointId: String, payload: ByteArray) {}
    override fun onHeartbeatAck(endpointId: String, challengeId: String) {}
    override fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?) { broadcasts += payload to excludeEndpointId }
    override fun onMessageSeen(msgId: String, readerName: String) {}
    override fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>) {}
    override fun onPublicKeyReceived(senderName: String, senderNodeId: String, key: String) {}
    override fun onRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>, topologySequence: Long) {
        routingTables += senderNodeId
    }
    override fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String) {
        received += msgId
    }
    override fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray) {}
    override fun onDeviceNameSync(endpointId: String, realName: String) {}
    override fun onDeviceGoodbye(endpointId: String) { goodbyes += endpointId }
    override fun onSosCancelled() {}
    override fun onBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {}
    override fun onBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope) {}
    override fun onLegacyBlockControl(payloadType: String, senderName: String) {}
    override fun showNotification(sender: String, text: String) {}
    override fun showSosEmergencyNotification(sender: String, text: String) {}
}
