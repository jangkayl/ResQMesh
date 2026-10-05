package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback

internal const val MAX_PRIVATE_RELAY_HOPS = 3

interface PayloadHandler {
    fun canHandle(payloadType: String): Boolean
    fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback)
}
