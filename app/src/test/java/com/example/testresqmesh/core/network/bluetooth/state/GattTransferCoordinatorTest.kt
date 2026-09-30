package com.example.testresqmesh.core.network.bluetooth.state

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GattTransferCoordinatorTest {
    @Test fun enqueuePreservesPriorityAndRejectsWorkBeyondTheQueueBound() {
        val store = BleStateStore()
        val coordinator = GattTransferCoordinator(store)
        val ordinary = GattTransfer(byteArrayOf(1))
        val priority = GattTransfer(byteArrayOf(2))

        assertTrue(coordinator.enqueue("peer", ordinary, priority = false))
        assertTrue(coordinator.enqueue("peer", priority, priority = true))
        repeat(MeshFrameCodec.MAX_PENDING_TRANSFERS - OutboundQueuePolicy.RESERVED_CONTROL_TRANSFERS - 2) {
            assertTrue(coordinator.enqueue("peer", GattTransfer(byteArrayOf(3)), priority = false))
        }

        assertSame(priority, store.pendingQueues["peer"]?.first)
        assertFalse(coordinator.enqueue("peer", GattTransfer(byteArrayOf(4)), priority = false))
        repeat(OutboundQueuePolicy.RESERVED_CONTROL_TRANSFERS) {
            assertTrue(coordinator.enqueue("peer", GattTransfer(byteArrayOf(5)), priority = true))
        }
        assertFalse(coordinator.enqueue("peer", GattTransfer(byteArrayOf(6)), priority = true))
        assertEquals(MeshFrameCodec.MAX_PENDING_TRANSFERS, store.pendingQueues["peer"]?.size)
    }

    @Test fun claimAndCompleteKeepsOneFlightUntilTheWholeFrameFinishes() {
        val fixture = readyServerFixture()
        val transfer = GattTransfer(byteArrayOf(1, 2, 3, 4))
        fixture.queue.addLast(transfer)

        val flight = fixture.coordinator.claimNext("peer", fixture.link, null, null)!!
        assertSame(transfer, flight.transfer)
        assertTrue(fixture.writing.get())
        assertNull(fixture.coordinator.claimNext("peer", fixture.link, null, null))

        fixture.coordinator.beginChunk(flight, 2)
        assertEquals(GattTransferCoordinator.Completion.MORE, fixture.coordinator.completeChunk(flight))
        fixture.coordinator.beginChunk(flight, 2)
        assertEquals(GattTransferCoordinator.Completion.DONE, fixture.coordinator.completeChunk(flight))
        assertFalse(fixture.writing.get())
    }

    @Test fun staleGenerationCannotOwnOrCompleteReplacementFlight() {
        val fixture = readyServerFixture()
        fixture.queue.addLast(GattTransfer(byteArrayOf(1)))
        val stale = fixture.coordinator.claimNext("peer", fixture.link, null, null)!!
        fixture.store.links.begin("peer", BleLinkRole.SERVER, null, fixture.queue, fixture.writing)

        assertFalse(fixture.coordinator.owns(stale))
        assertFalse(fixture.coordinator.callbackMatches(stale, BleLinkRole.SERVER, null, null))
        fixture.coordinator.beginChunk(stale, 1)
        assertEquals(GattTransferCoordinator.Completion.STALE, fixture.coordinator.completeChunk(stale, now = 10L))
        assertEquals(0L, fixture.store.links.current("peer", BleLinkRole.SERVER)!!.lastAcknowledgedWriteAt)
    }

    @Test fun activeVoiceBytesCountAgainstAdmissionButLeaveControlHeadroom() {
        val f = readyServerFixture()
        val voice = GattTransfer(ByteArray(OutboundQueuePolicy.MAX_ORDINARY_BYTES))
        assertTrue(f.coordinator.enqueue("peer", voice, false))
        val active = f.coordinator.claimNext("peer", f.link, null, null)!!
        assertFalse(f.coordinator.enqueue("peer", GattTransfer(byteArrayOf(1)), false))
        assertTrue(f.coordinator.enqueue("peer", GattTransfer(byteArrayOf(2)), true))
        f.coordinator.beginChunk(active, 20)
        assertEquals(GattTransferCoordinator.Completion.MORE, f.coordinator.completeChunk(active, now = 50L))
        assertEquals(50L, f.link.lastAcknowledgedWriteAt)
    }

    @Test fun fallbackAndControlTransfersRemainFifoAheadOfOrdinaryQueue() {
        val store = BleStateStore()
        val coordinator = GattTransferCoordinator(store)
        val ordinary = GattTransfer(byteArrayOf(0))
        val first = GattTransfer(byteArrayOf(1))
        val second = GattTransfer(byteArrayOf(2))
        coordinator.enqueue("peer", ordinary, false)
        coordinator.enqueue("peer", first, true)
        coordinator.enqueue("peer", second, true)
        assertEquals(listOf(first, second, ordinary), store.pendingQueues["peer"]!!.toList())
    }

    @Test fun promotionReturnsActiveTransferBeforeQueuedTransfersAndReleasesWriter() {
        val fixture = readyServerFixture()
        val active = GattTransfer(byteArrayOf(1))
        val queued = GattTransfer(byteArrayOf(2))
        fixture.queue.addLast(active)
        val flight = fixture.coordinator.claimNext("peer", fixture.link, null, null)!!
        fixture.queue.addLast(queued)

        assertEquals(listOf(active, queued), fixture.coordinator.drainForPromotion("peer"))
        assertFalse(fixture.writing.get())
        assertFalse(fixture.coordinator.owns(flight))
    }

    private fun readyServerFixture(): Fixture {
        val store = BleStateStore()
        val queue = ConcurrentLinkedDeque<GattTransfer>()
        val writing = AtomicBoolean(false)
        val link = store.links.begin("peer", BleLinkRole.SERVER, null, queue, writing, now = 1L)
        assertTrue(store.links.transition(link, BleLinkState.CONFIGURING, now = 2L))
        assertTrue(store.links.transition(link, BleLinkState.READY, now = 3L))
        store.pendingQueues["peer"] = queue
        store.isWriting["peer"] = writing
        return Fixture(store, queue, writing, link, GattTransferCoordinator(store))
    }

    private data class Fixture(
        val store: BleStateStore,
        val queue: ConcurrentLinkedDeque<GattTransfer>,
        val writing: AtomicBoolean,
        val link: BleLink,
        val coordinator: GattTransferCoordinator
    )
}
