package edu.cit.resqmesh.core.logging.trace

import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import edu.cit.resqmesh.core.network.TraceDropLimiter
import edu.cit.resqmesh.core.network.TransportDispatchResult
import edu.cit.resqmesh.feature.devtools.ui.hopLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PacketTraceEmitterTest {
    private var now = 0L

    @Before fun setUp() {
        TraceGate.set(true)
        PacketTraceEmitter.limiter = TraceDropLimiter(500L) { now }
    }

    @After fun tearDown() {
        TraceGate.set(false)
        PacketTraceEmitter.limiter = TraceDropLimiter()
    }

    private fun all() = TraceStore.dataSnapshot() + TraceStore.controlSnapshot()

    @Test fun sendResultsMapToDropReasons() {
        val m = TransportDispatchResult.entries.associateWith(PacketTraceEmitter::dropReasonOf)
        assertEquals(TraceDropReason.NONE, m[TransportDispatchResult.ACCEPTED])
        assertEquals(TraceDropReason.SEND_NOT_READY, m[TransportDispatchResult.REJECTED_NOT_READY])
        assertEquals(TraceDropReason.SEND_QUEUE_FULL, m[TransportDispatchResult.REJECTED_QUEUE_FULL])
        assertEquals(TraceDropReason.SEND_INVALID_FRAME, m[TransportDispatchResult.REJECTED_INVALID_FRAME])
        assertEquals(TraceDropReason.SEND_INVALID_ENDPOINT, m[TransportDispatchResult.REJECTED_INVALID_ENDPOINT])
    }

    @Test fun dropEventsAreRateLimitedPerEndpointAndReason() {
        val p = MeshPayload(id = "abc", senderNodeId = "AAAA1111BBBB")
        repeat(50) { PacketTraceEmitter.drop(p, TraceDropReason.DUPLICATE, "ep1", 10, "GATT") }
        assertEquals(1, all().size)
        PacketTraceEmitter.drop(p, TraceDropReason.EMPTY_ID, "ep1", 10, "GATT")
        PacketTraceEmitter.drop(p, TraceDropReason.DUPLICATE, "ep2", 10, "GATT")
        assertEquals(3, all().size)
        now += 500L
        PacketTraceEmitter.drop(p, TraceDropReason.DUPLICATE, "ep1", 10, "GATT")
        assertEquals(4, all().size)
        assertTrue(all().all { it.stage == TraceStage.DROPPED && it.dropReason != TraceDropReason.NONE })
    }

    @Test fun gateOffRecordsNothing() {
        TraceGate.set(false)
        PacketTraceEmitter.drop(MeshPayload(id = "x"), TraceDropReason.DUPLICATE, "ep", 1, null)
        PacketTraceEmitter.localOutcome("m1", TraceScope.PRIVATE, TraceType.MESSAGE, TraceDropReason.EXPIRED_UNSENT)
        TraceGate.set(true)
        assertTrue(all().isEmpty())
    }

    @Test fun routeStepFindsNodePosition() {
        val route = listOf("AAA", "BBB", "CCC")
        assertEquals(1, PacketTraceEmitter.routeStepOf(route, "bbb"))
        assertNull(PacketTraceEmitter.routeStepOf(route, "ZZZ"))
        assertNull(PacketTraceEmitter.routeStepOf(emptyList(), "AAA"))
        assertNull(PacketTraceEmitter.routeStepOf(route, null))
    }

    @Test fun inboundEventKeepsRouteAndNoSenderForReceipts() {
        val p = MeshPayload(id = "r1", type = "DELIVERED", senderNodeId = "AAAA1111BBBB", targetMessageId = "m1",
            isPrivate = true, directedRouteNodeIds = listOf("N1", "N2", "N3"))
        PacketTraceEmitter.emit(p, TraceStage.RECEIVED, TraceDir.IN, 5, "GATT", selfId = "N2")
        val t = all().single()
        assertNull(t.sourceId)
        assertEquals(1, t.routeStep)
        assertEquals(3, t.pathLen)
    }

    private fun trace(scope: TraceScope, hop: Int?, ttl: Int?, step: Int? = null, path: Int? = null) = PacketTrace(
        1, 1, TraceStage.RECEIVED, TraceDir.IN, scope, TraceType.MESSAGE, "k",
        null, null, null, hop, ttl, path, 1, null,
        routeStep = step
    )

    @Test fun hopLabelsAreHonest() {
        assertEquals("hop 2 · TTL left 5", hopLabel(trace(TraceScope.PUBLIC, 2, 5)))
        assertFalse(hopLabel(trace(TraceScope.PUBLIC, 2, 0)).contains("left"))
        assertEquals("route step 1/2", hopLabel(trace(TraceScope.PRIVATE, 0, 0, step = 1, path = 3)))
        assertFalse(hopLabel(trace(TraceScope.PRIVATE, 3, 5, step = null, path = 3)).contains("left"))
    }
}
