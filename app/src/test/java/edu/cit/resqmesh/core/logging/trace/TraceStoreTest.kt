package edu.cit.resqmesh.core.logging.trace

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogLevel
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.PacketTraceEmitter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class TraceStoreTest {
    @Before fun setUp() { TraceGate.set(true) }
    @After fun tearDown() { TraceGate.set(false) }

    private fun log(level: TraceLevel, msg: String = "m") = LogTrace(
        TraceStore.nextSeq(), 0L, level, 0, "ev", msg, "tag", null, null, null, null, false
    )

    @Test fun ringWrapsAndKeepsNewestInOrder() {
        val ring = TraceRing<Int>(5)
        (1..12).forEach(ring::add)
        assertEquals(listOf(8, 9, 10, 11, 12), ring.snapshot())
    }

    @Test fun concurrentWritesLoseNothingWithinCapacity() {
        val ring = TraceRing<Int>(10_000)
        val start = CountDownLatch(1)
        val threads = (0 until 4).map { t ->
            thread { start.await(); repeat(2_000) { ring.add(t * 10_000 + it) } }
        }
        start.countDown(); threads.forEach { it.join() }
        assertEquals(8_000, ring.snapshot().size)
        assertEquals(8_000, ring.snapshot().toSet().size)
    }

    @Test fun concurrentWritesPastCapacityStayBounded() {
        val ring = TraceRing<Int>(100)
        val threads = (0 until 4).map { thread { repeat(5_000) { i -> ring.add(i) } } }
        threads.forEach { it.join() }
        assertEquals(100, ring.snapshot().size)
    }

    @Test fun gateOffStoresNothingAndTurningOffClears() {
        TraceStore.addLog(log(TraceLevel.ERROR))
        assertEquals(1, TraceStore.errorsSnapshot().size)
        assertEquals(1, TraceStore.errorCount.value)
        TraceGate.set(false)
        assertTrue(TraceStore.logsSnapshot().isEmpty())
        assertTrue(TraceStore.errorsSnapshot().isEmpty())
        assertEquals(0, TraceStore.errorCount.value)
        val before = TraceStore.changeCount
        TraceStore.addLog(log(TraceLevel.ERROR))
        AppLogger.event(edu.cit.resqmesh.core.logging.TerminalLogCategory.SYSTEM, "e", "m", TerminalLogLevel.ERROR)
        PacketTraceEmitter.emit(MeshPayload(id = "x"), TraceStage.QUEUED, TraceDir.OUT, 1, "GATT")
        assertEquals(before, TraceStore.changeCount)
        assertTrue(TraceStore.dataSnapshot().isEmpty() && TraceStore.errorsSnapshot().isEmpty())
    }

    @Test fun logFloodDoesNotEvictErrors() {
        TraceStore.addLog(log(TraceLevel.ERROR, "keep"))
        repeat(TraceStore.LOGS_CAPACITY * 3) { TraceStore.addLog(log(TraceLevel.INFO)) }
        assertEquals(1, TraceStore.errorsSnapshot().size)
        assertEquals(TraceStore.LOGS_CAPACITY, TraceStore.logsSnapshot().size)
    }

    @Test fun macRedactedAtSnapshotNotAtWrite() {
        val mac = "AA:BB:CC:DD:EE:FF"
        AppLogger.d("BLE_MESH", "connect to $mac")
        assertTrue(TraceStore.logsSnapshot().any { it.message.contains(mac) })
        val shown = AppLogger.logs.value.single().message
        assertFalse(shown.contains(mac))
        assertTrue(shown.contains("…EE:FF"))
    }

    @Test fun warnAndErrorReachErrorsRing() {
        AppLogger.d("BLE_MESH", "link failed badly")
        AppLogger.d("BLE_MESH", "all fine")
        assertEquals(1, TraceStore.errorsSnapshot().size)
        assertEquals(2, TraceStore.logsSnapshot().size)
    }

    @Test fun emitsNeverThrowOnOddInput() {
        PacketTraceEmitter.emit(null, TraceStage.RECEIVED, TraceDir.IN, 0, null)
        val odd = MeshPayload(
            id = "", type = "\u0000weird\n", senderNodeId = "x".repeat(10_000),
            targetNodeId = " ", directedRouteNodeIds = listOf("", "a")
        )
        PacketTraceEmitter.emit(odd, TraceStage.RECEIVED, TraceDir.IN, -5, "")
        val t = TraceStore.dataSnapshot().single()
        assertEquals(TraceType.OTHER, t.type)
        assertEquals(8, t.sourceId?.length)
        assertEquals(null, t.destId)
    }

    @Test fun controlTypesGoToControlRing() {
        PacketTraceEmitter.emit(MeshPayload(id = "a", type = "PONG"), TraceStage.RECEIVED, TraceDir.IN, 1, null)
        PacketTraceEmitter.emit(MeshPayload(id = "b", type = "MESSAGE"), TraceStage.RECEIVED, TraceDir.IN, 1, null)
        assertEquals(1, TraceStore.controlSnapshot().size)
        assertEquals(1, TraceStore.dataSnapshot().size)
    }
}
