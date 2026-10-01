package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Additive READY notification; it does not participate in admission or link ownership. */
class MeshReadyPeerEvents {
    private val _peers = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val peers = _peers.asStateFlow()
    private val _events = MutableSharedFlow<ConnectedDevice>(replay = 3, extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun publish(peer: ConnectedDevice) {
        if (peer.isPayloadReady && peer.nodeId.isNotBlank()) _events.tryEmit(peer)
    }

    /** Current state survives missed notifications and removes retired/replaced endpoints. */
    fun update(peers: List<ConnectedDevice>) {
        _peers.value = peers.filter { it.isPayloadReady && !it.isProvisional && it.nodeId.isNotBlank() }
            .distinctBy { it.nodeId }
    }
}
