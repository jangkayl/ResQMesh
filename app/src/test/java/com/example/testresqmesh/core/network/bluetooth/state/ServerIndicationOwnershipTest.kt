package com.example.testresqmesh.core.network.bluetooth.state

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class ServerIndicationOwnershipTest {
    private val store = BleStateStore()
    private fun flight(endpoint: String = "same-address", role: BleLinkRole = BleLinkRole.SERVER): GattTransferFlight {
        val queue = ConcurrentLinkedDeque<GattTransfer>()
        val writing = AtomicBoolean(true)
        val link = store.links.begin(endpoint, role, "peer", queue, writing)
        store.links.transition(link, BleLinkState.CONFIGURING)
        store.links.transition(link, BleLinkState.READY)
        return GattTransferFlight(GattTransfer(byteArrayOf(1)), link, queue, writing, null, null).also {
            it.operationId = 1; store.gattFlights[endpoint] = it
        }
    }
    @Test fun addressCompletionUsesTheIssuedOwnerAndAllowsImmediateNextChunk() {
        val f = flight(); val ledger = store.serverIndications
        ledger.reset(4)
        val ticket = ledger.begin(f, 1)!!
        assertSame(ticket, ledger.take(String(charArrayOf('s','a','m','e','-','a','d','d','r','e','s','s')), 4))
        assertSame(f, ticket.owner)
        assertNotNull(ledger.begin(f, 2))
    }
    @Test fun retiredUnresolvedIndicationCannotBeAssignedToReplacement() {
        val old = flight(); val ledger = store.serverIndications; ledger.reset(1)
        val ticket = ledger.begin(old, 1)!!
        assertTrue(store.retireServerOwnership(old.link) {})
        val replacement = flight()
        assertNull(ledger.begin(replacement, 1))
        assertSame(ticket, ledger.take("same-address", 1))
        assertFalse(store.links.isCurrent(ticket.owner.link))
        assertSame(replacement, store.gattFlights["same-address"])
        assertNotNull(ledger.begin(replacement, 1))
    }
    @Test fun oldRegistrationCannotConsumeNewRegistrationCompletion() {
        val ledger = store.serverIndications; ledger.reset(1); ledger.begin(flight(), 1)
        ledger.reset(2)
        val replacement = flight(); val ticket = ledger.begin(replacement, 1)!!
        assertNull(ledger.take("same-address", 1))
        assertSame(ticket, ledger.take("same-address", 2))
    }
    @Test fun rejectedInitiationLeavesNoUnresolvedCallback() {
        val ledger = store.serverIndications; ledger.reset(1)
        ledger.rejected(ledger.begin(flight(), 1)!!)
        assertFalse(ledger.hasUnresolved())
    }
    @Test fun localRetirementWorksWithoutPlatformCallbackAndPreservesOtherOwners() {
        val old = flight(); val other = flight("other-peer"); val client = flight(role = BleLinkRole.CLIENT)
        val released = mutableListOf<String>()
        assertTrue(store.retireServerOwnership(old.link) { released += it })
        assertFalse(store.links.hasLiveRole("same-address", BleLinkRole.SERVER))
        assertTrue(store.links.isCurrent(other.link)); assertTrue(store.links.isCurrent(client.link))
        assertSame(client, store.gattFlights["same-address"])
        assertEquals(listOf(old.link.handshakeOwner), released)
        assertFalse(store.retireServerOwnership(old.link) {})
    }
}
