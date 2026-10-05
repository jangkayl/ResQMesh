package com.example.testresqmesh.core.network.transport

import android.os.Handler
import com.example.testresqmesh.core.network.OutboundFrameEvent
import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.GattTransfer
import com.example.testresqmesh.core.network.bluetooth.state.GattTransferCoordinator
import com.example.testresqmesh.core.network.bluetooth.state.MeshFrameCodec
import com.example.testresqmesh.core.network.bluetooth.state.OutboundQueuePolicy
import com.example.testresqmesh.core.network.bluetooth.state.payloadBytes
import java.util.concurrent.ConcurrentLinkedDeque
import org.junit.Assert.*
import org.junit.Test

class NativeOutboundDispatcherTest {
    private class Fixture {
        val store = BleStateStore()
        val coordinator = GattTransferCoordinator(store)
        val calls = mutableListOf<String>()
        var l2cap = TransportDispatchResult.REJECTED_NOT_READY
        val dispatcher = NativeOutboundDispatcher(
            store = store,
            handler = Handler(),
            peerTransferVersions = emptyMap(),
            nodeIdForEndpoint = { "B002" },
            hasReadyEndpoint = { true },
            isDeviceBlocked = { false },
            hasUsableL2cap = { false },
            transfers = { error("Ordinary wire frames must not initialize a transfer journal") },
            coordinator = { coordinator },
            sendL2cap = { _, _, _ -> calls += "l2cap"; l2cap },
            processNextPayload = { endpoint ->
                assertTrue(store.pendingQueues[endpoint]?.isNotEmpty() == true)
                calls += "drain"
            },
            reportFrame = { _, _, stage, transport ->
                assertEquals(OutboundFrameEvent.Stage.QUEUED, stage)
                calls += "reported-$transport"
            },
            removeHeartbeat = { error("An ordinary enqueue must not change heartbeat ownership") }
        )
    }

    @Test fun acceptedGattEnqueuePrecedesTheExistingDrainCall() {
        val f = Fixture()
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(f.dispatcher.enqueueGattPayload("endpoint", bytes, priority = true, heartbeatId = "challenge"))
        val queued = f.store.pendingQueues["endpoint"]!!.first
        assertArrayEquals(bytes, queued.payloadBytes())
        assertTrue(queued.priority)
        assertEquals("challenge", queued.heartbeatId)
        assertEquals(listOf("drain"), f.calls)
    }

    @Test fun fullGattQueueRejectsWithoutDrainingOrReportingAcceptance() {
        val f = Fixture()
        val bytes = byteArrayOf(1, 2, 3)
        val frame = MeshFrameCodec.encode(bytes)!!
        val limit = MeshFrameCodec.MAX_PENDING_TRANSFERS - OutboundQueuePolicy.RESERVED_CONTROL_TRANSFERS
        f.store.pendingQueues["endpoint"] = ConcurrentLinkedDeque(List(limit) { GattTransfer(frame) })
        assertFalse(f.dispatcher.enqueueGattPayload("endpoint", bytes))
        assertEquals(limit, f.store.pendingQueues["endpoint"]!!.size)
        assertTrue(f.calls.isEmpty())
    }

    @Test fun l2capPressureDoesNotCreateACompetingGattLane() {
        val f = Fixture()
        f.l2cap = TransportDispatchResult.REJECTED_QUEUE_FULL
        assertEquals(TransportDispatchResult.REJECTED_QUEUE_FULL,
            f.dispatcher.sendRawPayload("endpoint", byteArrayOf(1, 2, 3), false))
        assertNull(f.store.pendingQueues["endpoint"])
        assertEquals(listOf("l2cap"), f.calls)
    }

    @Test fun unavailableL2capFallsBackToGattAndReportsBeforeDrain() {
        val f = Fixture()
        assertEquals(TransportDispatchResult.ACCEPTED,
            f.dispatcher.sendRawPayload("endpoint", byteArrayOf(1, 2, 3), false))
        assertEquals(listOf("l2cap", "reported-GATT", "drain"), f.calls)
    }
}
