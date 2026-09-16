package com.example.testresqmesh.core.network.bluetooth.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedDeque

class HeartbeatChallengesTest {
    @Test fun ackMustEchoNonceAndCurrentGeneration() {
        val challenge = HeartbeatChallenge("peer", BleLinkRole.CLIENT, 8L, "HB:unique", 1_000L)
        assertTrue(challenge.accepts("peer", "HB:unique", 8L))
        assertFalse(challenge.accepts("peer", "HB:old", 8L))
        assertFalse(challenge.accepts("other", "HB:unique", 8L))
        assertFalse(challenge.accepts("peer", "HB:unique", 9L))
    }

    @Test fun replyDeadlineStartsAfterFinalGattChunkCompletes() {
        val queued = HeartbeatChallenge("peer", BleLinkRole.SERVER, 4L, "HB:unique", 1_000L)
        assertFalse(queued.expired(50_000L, 8_000L))
        val sent = queued.copy(sentAt = 3_000L)
        assertFalse(sent.expired(10_999L, 8_000L))
        assertTrue(sent.expired(11_000L, 8_000L))
    }

    @Test fun priorityProbeOvertakesQueuedTransfersButNotAnActiveFrame() {
        val queue = ConcurrentLinkedDeque<GattTransfer>()
        val active = GattTransfer(byteArrayOf(1, 2, 3))
        val later = GattTransfer(byteArrayOf(4, 5))
        val probe = GattTransfer(byteArrayOf(6), "HB:unique")
        queue.addLast(active)
        queue.addLast(later)
        assertTrue(queue.pollFirst() === active)
        queue.addFirst(probe)
        assertTrue(queue.pollFirst() === probe)
        assertTrue(queue.pollFirst() === later)
    }

    @Test fun promotedGattTransferDropsOnlyItsLengthPrefix() {
        val transfer = GattTransfer(byteArrayOf(0, 0, 0, 3, 7, 8, 9))
        assertArrayEquals(byteArrayOf(7, 8, 9), transfer.payloadBytes())
    }
}
