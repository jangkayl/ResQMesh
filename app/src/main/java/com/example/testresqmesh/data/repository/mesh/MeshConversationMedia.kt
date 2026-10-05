package com.example.testresqmesh.data.repository.mesh

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.MeshNetworkGateway
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.testresqmesh.data.repository.MeshRouter

internal class MeshConversationMedia(
    private val networkManager: MeshNetworkGateway,
    private val repositoryScope: CoroutineScope,
    private val meshRouter: MeshRouter,
    private val _currentChannelId: MutableStateFlow<String>,
    private val _connectedDevices: MutableStateFlow<List<ConnectedDevice>>,
    private val _incomingSosAlert: MutableStateFlow<ChatMessage?>,
    private val incomingLiveAudioChunk: kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, ByteArray>>,
    private val localName: () -> String,
    private val readyPeers: () -> List<ConnectedDevice>
) {
    private val myNodeName get() = localName()
    private fun readyConnectedDevices() = readyPeers()



    fun onNetworkSosCancelled(): Unit = run { /* Unscoped legacy cancellation is ignored. */ }

    fun onNetworkLiveAudioChunk(sender: String, channelId: String, chunk: ByteArray): Unit = run {
        if (channelId == _currentChannelId.value) {
            repositoryScope.launch {
                incomingLiveAudioChunk.emit(Pair(sender, chunk))
            }
        }
    }

    fun broadcastLiveAudioChunk(chunk: ByteArray) {
        val payload = com.example.testresqmesh.core.network.MeshPayload(
            id = UUID.randomUUID().toString(),
            type = "LIVE_AUDIO",
            senderName = myNodeName,
            channelId = _currentChannelId.value,
            liveAudioChunk = chunk
        )
        val payloadBytes = kotlinx.serialization.protobuf.ProtoBuf.encodeToByteArray(com.example.testresqmesh.core.network.MeshPayload.serializer(), payload)

        // STP Directed Routing: Only initiate stream to Spanning Tree neighbors
        val stpNeighbors = meshRouter.getSpanningTreeNeighbors(myNodeName, readyConnectedDevices())

        // Better yet: just send to connected devices whose name is in stpNeighbors
        _connectedDevices.value.forEach { device ->
            if (NodeIdentity.matchesAny(device.name, stpNeighbors)) {
                networkManager.sendDirectPayload(device.endpointId, payloadBytes)
            }
        }
    }

    fun setChannel(channelId: String) {
        _currentChannelId.value = channelId
    }

    fun clearSosAlert() {
        _incomingSosAlert.value = null
    }
}
