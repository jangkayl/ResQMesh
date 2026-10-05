package com.example.testresqmesh.core.network.transport

import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.core.network.OutboundFrameEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class OutboundFrameReporterTest {
    @Test fun ordinaryProgressUsesTheCurrentListenerAndLogicalMessageIdentity() {
        var oldCalls = 0
        var listener: ((OutboundFrameEvent) -> Unit)? = { oldCalls++ }
        val reporter = OutboundFrameReporter(
            readyGattLink = { null },
            nodeIdForEndpoint = { "b002" },
            transfers = { error("Completion without relay custody must not touch the journal") },
            removeLegacyFrame = { key, endpoint ->
                assertEquals("B002:transmission", key)
                assertEquals("endpoint", endpoint)
                false
            },
            listener = { listener }
        )
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = "transmission", type = "MESSAGE",
            targetMessageId = "logical", isPrivate = true, directedRouteNodeIds = listOf("A001", "B002", "C003")))
        var observed: OutboundFrameEvent? = null
        listener = { observed = it }
        reporter.reportFrame("endpoint", bytes, OutboundFrameEvent.Stage.COMPLETED, "GATT", 100L, 150L)
        assertEquals(0, oldCalls)
        assertEquals("transmission", observed!!.transmissionId)
        assertEquals("logical", observed!!.messageId)
        assertEquals(OutboundFrameEvent.Stage.COMPLETED, observed!!.stage)
        assertEquals(50L, observed!!.queueMs)
        assertEquals(bytes.size, observed!!.bytes)
        assertEquals(2, observed!!.hops)
        assertTrue(observed!!.isPrivate)
    }

    @Test fun failedFrameRemovesCapturedRelayOwnershipBeforePublishing() {
        val calls = mutableListOf<String>()
        val reporter = OutboundFrameReporter(
            readyGattLink = { null },
            nodeIdForEndpoint = { "B002" },
            transfers = { error("A failed legacy frame is not recipient delivery") },
            removeLegacyFrame = { key, endpoint ->
                assertEquals("B002:transmission", key)
                assertEquals("endpoint", endpoint)
                calls += "remove"
                true
            },
            listener = { { calls += "publish" } }
        )
        val bytes = ProtoBuf.encodeToByteArray(MeshPayload(id = "transmission", type = "MESSAGE"))
        reporter.reportFrame("endpoint", bytes, OutboundFrameEvent.Stage.FAILED, "GATT")
        assertEquals(listOf("remove", "publish"), calls)
    }
}
