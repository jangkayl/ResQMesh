package com.example.testresqmesh.core.network

import org.junit.Assert.*
import org.junit.Test

class DirectedReceiptQueueTest {
    private class Fixture {
        var time = 0L; var sends = 0; var result = TransportDispatchResult.REJECTED_QUEUE_FULL
        val timers = mutableListOf<Pair<Long, () -> Unit>>()
        val queue = DirectedReceiptQueue({ sends++; result }, { ms, action -> timers += ms to action }, { time })
        fun receipt(id: String = "logical") = MeshPayload(id = "wire-$id", type = "DELIVERED", isPrivate = true,
            targetMessageId = id, reader = "Bob#B001", directedRouteNodeIds = listOf("B001", "A001"))
    }
    @Test fun healthyFirstAttemptDoesNotWaitOrArmATimer() {
        val f = Fixture(); f.result = TransportDispatchResult.ACCEPTED
        f.queue.offer(f.receipt())
        assertEquals(1, f.sends); assertEquals(0, f.queue.size); assertTrue(f.timers.isEmpty()); assertEquals(0L, f.time)
    }
    @Test fun rejectionIsRetainedAndReadyWakeRetriesImmediately() {
        val f = Fixture(); f.queue.offer(f.receipt()); assertEquals(1, f.queue.size)
        f.result = TransportDispatchResult.ACCEPTED; f.queue.wake()
        assertEquals(2, f.sends); assertEquals(0L, f.time); assertEquals(0, f.queue.size)
        f.timers.single().second(); assertEquals(2, f.sends)
    }
    @Test fun duplicateReceiptsCoalesceWithOneRetryTimer() {
        val f = Fixture(); repeat(20) { f.queue.offer(f.receipt().copy(id = "attempt-$it")) }
        assertEquals(1, f.queue.size); assertEquals(1, f.timers.size)
        f.time = 5000; f.result = TransportDispatchResult.ACCEPTED; f.timers.single().second()
        assertEquals(0, f.queue.size)
    }
    @Test fun retainedReceiptsAreBoundedAndExpire() {
        val f = Fixture(); repeat(140) { f.queue.offer(f.receipt("$it")) }
        assertEquals(128, f.queue.size); assertEquals(128, f.sends)
        f.time = 24 * 60 * 60 * 1000L; f.queue.wake()
        assertEquals(0, f.queue.size); assertEquals(128, f.sends)
    }
    @Test fun clearedSessionCannotReplayItsOldTimer() {
        val f = Fixture(); f.queue.offer(f.receipt()); val oldTimer = f.timers.single().second
        f.queue.clear(); f.result = TransportDispatchResult.ACCEPTED; oldTimer()
        assertEquals(1, f.sends); assertEquals(0, f.queue.size)
    }
}
