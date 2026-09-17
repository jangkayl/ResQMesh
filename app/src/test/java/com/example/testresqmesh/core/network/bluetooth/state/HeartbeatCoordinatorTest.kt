package com.example.testresqmesh.core.network.bluetooth.state

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

    @Test fun expectedRemovalCannotDeleteAReplacementChallenge() {
        val coordinator = HeartbeatCoordinator { 100L }
        val old = coordinator.begin("peer", BleLinkRole.CLIENT, 1L, "old")!!
        assertTrue(coordinator.remove("peer", old))
        val replacement = coordinator.begin("peer", BleLinkRole.CLIENT, 2L, "new")!!
        assertFalse(coordinator.remove("peer", old))
        assertTrue(coordinator.pending("peer") === replacement)
    }
}
