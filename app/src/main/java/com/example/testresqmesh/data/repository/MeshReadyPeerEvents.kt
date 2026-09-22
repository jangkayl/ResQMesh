package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Additive READY notification; it does not participate in admission or link ownership. */
class MeshReadyPeerEvents {
    private val _events = MutableSharedFlow<ConnectedDevice>(replay = 3, extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun publish(peer: ConnectedDevice) {
        if (peer.isPayloadReady && peer.nodeId.isNotBlank()) _events.tryEmit(peer)
    }
}
