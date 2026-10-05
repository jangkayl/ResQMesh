package com.example.testresqmesh.core.network.dispatch

import com.example.testresqmesh.core.network.BlockControlEnvelope
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.MessageReceivedCallback
import com.example.testresqmesh.core.network.NativePayloadEvents
import com.example.testresqmesh.core.network.TransportDispatchResult
import org.junit.Assert.*
import org.junit.Test

class NativePayloadCallbacksTest {
    private class Events : NativePayloadEvents {
        override var myDeviceName = "Original#A001"
        override var myNodeId = "A001"
        override var onConversationMessage: ((String, MeshPayload) -> Unit)? = null
        override var onSosPacket: ((String, MeshPayload) -> Unit)? = null
        override var onMessageSeen: ((String, String) -> Unit)? = null
        override var onMessageDelivered: ((String, String, List<String>) -> Unit)? = null
        override var onPublicKeyReceived: ((String, String, String) -> Unit)? = null
        override var onRoutingTableReceived: ((String, String, List<String>, List<String>, Long) -> Unit)? = null
        override var onMessageReceived: MessageReceivedCallback? = null
        override var onLiveAudioChunk: ((String, String, ByteArray) -> Unit)? = null
        override var onSosCancelled: (() -> Unit)? = null
        override var onBlockRequest: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
        override var onBlockAck: ((String, MeshPayload, BlockControlEnvelope) -> Unit)? = null
        override var onDomainEvent: ((String, MeshPayload) -> Unit)? = null
        override var onEventSyncRequest: ((String, MeshPayload) -> Unit)? = null
        override var onEventSyncResponse: ((String, MeshPayload) -> Unit)? = null
        override var stpNeighborsProvider: (() -> Set<String>)? = null
    }

    private fun callbacks(events: Events, direct: (String, ByteArray) -> TransportDispatchResult = { _, _ -> TransportDispatchResult.ACCEPTED }) =
        NativePayloadCallbacks(
            events = events,
            seenMessageIds = { linkedSetOf() },
            endpointForPeer = { "endpoint-$it" },
            pendingCustody = { false },
            forwardPrivate = direct,
            direct = direct,
            priority = direct,
            receipt = {},
            gatt = { _, _ -> },
            heartbeatAck = { _, _ -> },
            broadcast = { _, _ -> },
            disconnect = {},
            privateNotification = { _, _ -> },
            sosNotification = { _, _ -> }
        )

    @Test fun listenerReplacementAndIdentityChangesAreVisibleAtInvocation() {
        val events = Events()
        val callback = callbacks(events)
        val payload = MeshPayload(id = "synthetic", type = "CONVERSATION", senderName = "Peer#B002")
        var oldCalls = 0
        events.onConversationMessage = { _, _ -> oldCalls++ }
        callback.onConversationMessage("endpoint", payload)
        var currentPayload: MeshPayload? = null
        events.onConversationMessage = { _, received -> currentPayload = received }
        events.myDeviceName = "Renamed#F006"
        events.myNodeId = "F006"
        events.stpNeighborsProvider = { setOf("B002") }
        callback.onConversationMessage("endpoint", payload)
        assertEquals(1, oldCalls)
        assertSame(payload, currentPayload)
        assertEquals("Renamed#F006", callback.getMyDeviceName())
        assertEquals("F006", callback.getMyNodeId())
        assertEquals(setOf("B002"), callback.getStpNeighbors())
    }

    @Test fun dispatchPreservesImmediateResultAndAllMessageArguments() {
        val events = Events()
        var calls = 0
        val bytes = byteArrayOf(1, 2, 3)
        val callback = callbacks(events) { endpoint, received ->
            calls++
            assertEquals("endpoint", endpoint)
            assertSame(bytes, received)
            TransportDispatchResult.REJECTED_QUEUE_FULL
        }
        assertEquals(TransportDispatchResult.REJECTED_QUEUE_FULL, callback.sendDirectPayload("endpoint", bytes))
        assertEquals(1, calls)
        var arguments: List<Any?>? = null
        events.onMessageReceived = { endpoint, id, sender, text, privateFlag, systemFlag, image, audio, lat, lng, medium, route, channel ->
            arguments = listOf(endpoint, id, sender, text, privateFlag, systemFlag, image, audio, lat, lng, medium, route, channel)
        }
        val route = listOf("A001", "B002")
        callback.onMessageReceived("endpoint", "id", "sender", "synthetic", true, false, null, "audio", 10.0, 123.0, "GATT", route, "2")
        assertEquals(listOf("endpoint", "id", "sender", "synthetic", true, false, null, "audio", 10.0, 123.0, "GATT", route, "2"), arguments)
        assertSame(route, arguments!![11])
    }
}
