package edu.cit.resqmesh.core.network.bluetooth.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatCoordinatorTest {
    @Test fun onlyOneChallengeMayOwnAnEndpoint() {
        val coordinator = HeartbeatCoordinator { 100L }
        assertNotNull(coordinator.begin("peer", BleLinkRole.CLIENT, 1L, "first"))
        assertNull(coordinator.begin("peer", BleLinkRole.CLIENT, 1L, "second"))
        assertTrue(coordinator.contains("peer"))
    }

    @Test fun sendAndAckMustMatchIdAndGeneration() {
        var clock = 100L
        val coordinator = HeartbeatCoordinator { clock }
        coordinator.begin("peer", BleLinkRole.SERVER, 4L, "challenge")
        clock = 250L
        val sent = coordinator.markSent("peer", 4L, "challenge")!!
        assertTrue(sent.sentAt == 250L)
        assertFalse(coordinator.acknowledge("peer", "wrong", 4L))
        assertFalse(coordinator.acknowledge("peer", "challenge", 5L))
        assertTrue(coordinator.acknowledge("peer", "challenge", 4L))
        assertFalse(coordinator.contains("peer"))
    }

    @Test fun missesAccumulateOnOneGenerationAndResetOnAck() {
        val coordinator = HeartbeatCoordinator { 100L }
        assertEquals(1, coordinator.recordMiss("peer", 3L))
        assertEquals(2, coordinator.recordMiss("peer", 3L))
        coordinator.begin("peer", BleLinkRole.CLIENT, 3L, "hb")
        coordinator.markSent("peer", 3L, "hb")
        assertTrue(coordinator.acknowledge("peer", "hb", 3L))
        assertEquals(1, coordinator.recordMiss("peer", 3L))
    }

    @Test fun replacementLinkDoesNotInheritMisses() {
        val coordinator = HeartbeatCoordinator { 100L }
        coordinator.recordMiss("peer", 1L)
        coordinator.recordMiss("peer", 1L)
        assertEquals(1, coordinator.recordMiss("peer", 2L))
    }

    @Test fun forgetAndResetClearMisses() {
        val coordinator = HeartbeatCoordinator { 100L }
        coordinator.recordMiss("peer", 1L)
        coordinator.resetMisses("peer")
        assertEquals(1, coordinator.recordMiss("peer", 1L))
        coordinator.begin("peer", BleLinkRole.CLIENT, 1L, "hb")
        coordinator.forget("peer")
        assertFalse(coordinator.contains("peer"))
        assertEquals(1, coordinator.recordMiss("peer", 1L))
    }

    @Test fun expectedRemovalCannotDeleteAReplacementChallenge() {
        val coordinator = HeartbeatCoordinator { 100L }
        val old = coordinator.begin("peer", BleLinkRole.CLIENT, 1L, "old")!!
        assertTrue(coordinator.remove("peer", old))
        val replacement = coordinator.begin("peer", BleLinkRole.CLIENT, 2L, "new")!!
        assertFalse(coordinator.remove("peer", old))
        assertTrue(coordinator.pending("peer") === replacement)
    }
}
