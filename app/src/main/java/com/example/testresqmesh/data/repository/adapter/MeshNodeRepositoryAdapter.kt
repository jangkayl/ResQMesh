package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.domain.repository.MeshNodeRepository
import com.example.testresqmesh.data.repository.MeshRepository

/** Delegates to [MeshRepository], whose flows already carry core types. */
@Suppress("TooManyFunctions")
class MeshNodeRepositoryAdapter(private val repository: MeshRepository) : MeshNodeRepository {
    override val connectionStatus get() = repository.connectionStatus
    override val isOnline get() = repository.isOnline
    override val currentChannelId get() = repository.currentChannelId
    override val connectedDevices get() = repository.connectedDevices
    override val scannedDevices get() = repository.scannedDevices
    override val incomingSosAlert get() = repository.incomingSosAlert
    override val incomingVoiceMessage get() = repository.incomingVoiceMessage
    override val incomingLiveAudioChunk get() = repository.incomingLiveAudioChunk
    override val publicSendFeedback get() = repository.publicSendFeedback
    override val knownNodes get() = repository.knownNodes
    override val topology get() = repository.topology
    override val publicMessages get() = repository.publicMessages
    override val allPublicMessages get() = repository.allPublicMessages
    override val privateMessages get() = repository.privateMessages
    override val peerNames get() = repository.peerNames
    override val blockedDeviceNames get() = repository.blockedDeviceNames

    override fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String) =
        repository.startNode(customName, nodeTag, teamKey, nodeId)
    override fun stopNode() = repository.stopNode()
    override fun reconcileTransport() = repository.reconcileTransport()
    override fun setChannel(channelId: String) = repository.setChannel(channelId)
    override fun disconnectDevice(endpointId: String) = repository.disconnectDevice(endpointId)
    override fun blockDevice(deviceName: String) = repository.blockDevice(deviceName)
    override fun unblockDevice(deviceName: String) = repository.unblockDevice(deviceName)
    override fun forceConnect(endpointId: String, endpointName: String) =
        repository.forceConnect(endpointId, endpointName)
    override fun rescan() = repository.rescan()
    override fun sendPublicMessage(
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double?,
        locationLng: Double?,
        isSOS: Boolean,
        isSOSCancel: Boolean,
        conversationKind: String,
        channelId: String,
        sosId: String
    ): String = repository.sendPublicMessage(
        text, imageBase64, audioBase64, locationLat, locationLng, isSOS, isSOSCancel,
        conversationKind, channelId, sosId
    )
    override fun sendPrivateMessage(
        targetName: String,
        text: String,
        imageBase64: String?,
        audioBase64: String?,
        locationLat: Double?,
        locationLng: Double?
    ): Boolean = repository.sendPrivateMessage(targetName, text, imageBase64, audioBase64, locationLat, locationLng)
    override fun hasPendingPublicKeyChange(peerName: String) = repository.hasPendingPublicKeyChange(peerName)
    override fun acceptPendingPublicKeyChange(peerName: String) = repository.acceptPendingPublicKeyChange(peerName)
    override fun rejectPendingPublicKeyChange(peerName: String) = repository.rejectPendingPublicKeyChange(peerName)
    override fun deleteConversationWith(peerName: String) = repository.deleteConversationWith(peerName)
    override fun broadcastLiveAudioChunk(chunk: ByteArray) = repository.broadcastLiveAudioChunk(chunk)
    override fun broadcastSeenReceipt(messageId: String, isPrivate: Boolean, targetName: String?) =
        repository.broadcastSeenReceipt(messageId, isPrivate, targetName)
    override fun clearSosAlert() = repository.clearSosAlert()
}
