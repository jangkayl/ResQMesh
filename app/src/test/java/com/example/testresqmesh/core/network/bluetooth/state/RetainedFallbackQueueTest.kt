package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.*
import org.junit.Test

class RetainedFallbackQueueTest {
    private class Fixture {
        var now = 1_000L
        var capacity = 0
        val sent = mutableListOf<Int>()
        val failed = mutableListOf<Int>()
        val scheduled = ArrayDeque<() -> Unit>()
        val queue = RetainedFallbackQueue<Int>(
            { _, item -> if (capacity > 0) { capacity--; sent.add(item); true } else false },
            { _, action -> scheduled.addLast(action) }, { _, item -> failed.add(item) }, { now })
        fun tick() { now += 200; scheduled.removeFirst()() }
    }

    @Test fun queuePressureRetainsAcceptedSocketFramesUntilGattHasRoom() {
        val f = Fixture()
        f.queue.retain("peer", listOf(1, 2, 3))
        assertTrue(f.queue.hasPending("peer")); assertTrue(f.sent.isEmpty())
        f.capacity = 1; f.tick()
        assertEquals(listOf(1), f.sent); assertTrue(f.queue.hasPending("peer"))
        f.capacity = 2; f.tick()
        assertEquals(listOf(1, 2, 3), f.sent); assertFalse(f.queue.hasPending("peer"))
        assertTrue(f.failed.isEmpty())
    }

    @Test fun retiringOnePeerReportsItsUnsentFramesOnceAndKeepsOtherPeer() {
        val f = Fixture()
        f.queue.retain("A", listOf(1, 2)); f.queue.retain("B", listOf(3))
        f.queue.retire("A"); f.queue.retire("A")
        assertEquals(listOf(1, 2), f.failed); assertTrue(f.queue.hasPending("B"))
        f.capacity = 1; f.tick(); assertEquals(listOf(3), f.sent)
    }

    @Test fun retentionExpiresExplicitlyAndDoesNotReplayExpiredFrames() {
        val f = Fixture(); f.queue.retain("peer", listOf(1))
        f.now += 24 * 60 * 60 * 1000L; f.capacity = 1; f.tick()
        assertEquals(listOf(1), f.failed); assertTrue(f.sent.isEmpty())
        assertFalse(f.queue.hasPending("peer"))
    }
}
