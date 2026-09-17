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
        repeat(MeshFrameCodec.MAX_PENDING_TRANSFERS - 2) {
            assertTrue(coordinator.enqueue("peer", GattTransfer(byteArrayOf(3)), priority = false))
        }

        assertSame(priority, store.pendingQueues["peer"]?.first)
        assertFalse(coordinator.enqueue("peer", GattTransfer(byteArrayOf(4)), priority = false))
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
