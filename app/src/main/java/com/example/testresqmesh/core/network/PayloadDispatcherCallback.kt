package com.example.testresqmesh.core.network

interface PayloadDispatcherCallback {
    fun getMyDeviceName(): String
    fun getSeenMessageIds(): java.util.LinkedHashSet<String>
    fun getEndpointMedium(endpointId: String): String
    fun getConnectedEndpointIdByName(name: String): String?
    
    fun sendDirectPayload(endpointId: String, payload: ByteArray)
    fun broadcastPayload(payload: ByteArray, excludeEndpointId: String?)
    
    fun onMessageSeen(msgId: String, readerName: String)
    fun onMessageDelivered(msgId: String, readerName: String, returnRoute: List<String>)
    fun onPublicKeyReceived(senderName: String, key: String)
    fun onRoutingTableReceived(senderName: String, connectedNodes: List<String>)
    fun onMessageReceived(endpointId: String, msgId: String, senderName: String, text: String, isPrivate: Boolean, isSystem: Boolean, imageBase64: String?, audioBase64: String?, locationLat: Double?, locationLng: Double?, medium: String, routePath: List<String>)
    
    fun onSosCancelled()
    
    fun showNotification(sender: String, text: String)
    fun showSosEmergencyNotification(sender: String, text: String)
}
