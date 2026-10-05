package com.example.testresqmesh.core.network

/** Live application listeners; no transport resources or scheduling are exposed. */
interface NativePayloadEvents {
    val onConversationMessage: ((String, MeshPayload) -> Unit)?
    val onSosPacket: ((String, MeshPayload) -> Unit)?
    val onMessageSeen: ((String, String) -> Unit)?
    val onMessageDelivered: ((String, String, List<String>) -> Unit)?
    val onPublicKeyReceived: ((String, String, String) -> Unit)?
    val onRoutingTableReceived: ((String, String, List<String>, List<String>, Long) -> Unit)?
    val onMessageReceived: MessageReceivedCallback?
    val onLiveAudioChunk: ((String, String, ByteArray) -> Unit)?
    val onSosCancelled: (() -> Unit)?
    val onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)?
    val onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)?
    val onDomainEvent: ((String, MeshPayload) -> Unit)?
    val onEventSyncRequest: ((String, MeshPayload) -> Unit)?
    val onEventSyncResponse: ((String, MeshPayload) -> Unit)?
    val stpNeighborsProvider: (() -> Set<String>)?
    val myDeviceName: String
    val myNodeId: String
}
