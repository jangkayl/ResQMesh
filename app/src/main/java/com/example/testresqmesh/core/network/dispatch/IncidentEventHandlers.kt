package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.PayloadDispatcherCallback
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class DomainEventHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "DOMAIN_EVENT"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        // IncidentRepository validates and only then relays accepted business events.  Relaying
        // here would spread malformed or unauthorized state changes before policy can reject them.
        callback.onDomainEvent(endpointId, payload)
    }
}

class EventSyncRequestHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "EVENT_SYNC_REQ"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onEventSyncRequest(endpointId, payload)
    }
}

class EventSyncResponseHandler : PayloadHandler {
    override fun canHandle(payloadType: String) = payloadType == "EVENT_SYNC_RESP"

    override fun handle(endpointId: String, payload: MeshPayload, payloadBytes: ByteArray, callback: PayloadDispatcherCallback) {
        callback.onEventSyncResponse(endpointId, payload)
    }
}
