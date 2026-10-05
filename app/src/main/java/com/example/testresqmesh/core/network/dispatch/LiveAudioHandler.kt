package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback

class LiveAudioHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "LIVE_AUDIO"
    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        val chunk = payload.liveAudioChunk ?: return
        val channelId = payload.channelId
        callback.onLiveAudioChunk(payload.senderName, channelId, chunk)
        val stpNeighbors = callback.getStpNeighbors()
        if (stpNeighbors.isEmpty()) return

        for (neighborName in stpNeighbors) {
            val neighborEndpointId = callback.getConnectedEndpointIdByName(neighborName)
            if (neighborEndpointId != null && neighborEndpointId != endpointId) {
                callback.sendDirectPayload(neighborEndpointId, payloadBytes)
            }
        }
    }
}
