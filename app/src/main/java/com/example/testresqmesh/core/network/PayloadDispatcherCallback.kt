package com.example.testresqmesh.core.network

interface PayloadDispatcherCallback {
    fun getMyDeviceName(): String
    fun getMyNodeId(): String
    fun getSeenMessageIds(): MutableSet<String>
    fun getEndpointMedium(endpointId: String): String
    fun getConnectedEndpointIdByName(name: String): String?
    fun getConnectedEndpointIdByNodeId(nodeId: String): String?
    fun getStpNeighbors(): Set<String>
    
    fun sendDirectPayload(endpointId: String, payload: ByteArray)
    fun sendPriorityPayload(endpointId: String, payload: ByteArray)
    fun sendGattPayload(endpointId: String, payload: ByteArray)
    fun onHeartbeatAck(endpointId: String, challengeId: String)
    fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?)
    
    fun onMessageSeen(msgId: String, readerName: String)
    fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>)
    fun onPublicKeyReceived(senderName: String, senderNodeId: String, key: String)
    fun onRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>)
    fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>, channelId: String)
    fun onLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray)
    
    fun onDeviceNameSync(endpointId: String, realName: String)
    fun onDeviceGoodbye(endpointId: String)
    fun onSosCancelled()
    
    fun onBlockRequest(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope)
    fun onBlockAck(endpointId: String, payload: MeshPayload, envelope: BlockControlEnvelope)
    fun onLegacyBlockControl(payloadType: String, senderName: String)
    
    fun showNotification(sender: String, text: String)
    fun showSosEmergencyNotification(sender: String, text: String)
}
