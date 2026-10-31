package edu.cit.resqmesh.core.domain.repository

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.KnownNode
import edu.cit.resqmesh.core.model.ScannedDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Mesh node session, peers and public/private messaging, as consumed above the data layer. */
@Suppress("TooManyFunctions")
interface MeshNodeRepository {
    val connectionStatus: StateFlow<String>
    val isOnline: StateFlow<Boolean>
    val currentChannelId: StateFlow<String>
    val connectedDevices: StateFlow<List<ConnectedDevice>>
    val scannedDevices: StateFlow<List<ScannedDevice>>
    val incomingSosAlert: StateFlow<ChatMessage?>
    val incomingVoiceMessage: SharedFlow<ChatMessage>
    val publicSendFeedback: SharedFlow<String>
    val knownNodes: StateFlow<List<KnownNode>>
    val topology: StateFlow<Map<String, Set<String>>>
    val publicMessages: StateFlow<List<ChatMessage>>
    val allPublicMessages: StateFlow<List<ChatMessage>>
    val privateMessages: StateFlow<Map<String, List<ChatMessage>>>
    val peerNames: StateFlow<Map<String, String>>
    val blockedDeviceNames: StateFlow<Set<String>>

    fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String)
    fun stopNode()
    fun reconcileTransport()
    fun setChannel(channelId: String)
    fun disconnectDevice(endpointId: String)
    fun blockDevice(deviceName: String)
    fun unblockDevice(deviceName: String)
    fun forceConnect(endpointId: String, endpointName: String)
    fun rescan()
    @Suppress("LongParameterList")
    fun sendPublicMessage(
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double? = null,
        locationLng: Double? = null,
        isSOS: Boolean = false,
        isSOSCancel: Boolean = false,
        conversationKind: String = "COMMUNITY",
        channelId: String = "",
        sosId: String = ""
    ): String
    @Suppress("LongParameterList")
    fun sendPrivateMessage(
        targetName: String,
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double? = null,
        locationLng: Double? = null
    ): Boolean
    fun hasPendingPublicKeyChange(peerName: String): Boolean
    fun acceptPendingPublicKeyChange(peerName: String): Boolean
    fun rejectPendingPublicKeyChange(peerName: String): Boolean
    fun deleteConversationWith(peerName: String)
    fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String? = null)
    fun clearSosAlert()
}
