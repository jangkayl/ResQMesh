package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PayloadDispatcherCallback

internal const val MAX_PRIVATE_RELAY_HOPS = 3

interface PayloadHandler {
    fun canHandle(payloadType: String): Boolean
    fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback)
}
