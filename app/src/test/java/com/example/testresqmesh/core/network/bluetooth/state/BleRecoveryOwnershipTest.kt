package com.example.testresqmesh.core.network.bluetooth.state

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class BleRecoveryOwnershipTest {
    @Test fun blockedUnknownEndpointRetiresWithoutNameLookupAndCannotRetireReplacement() {
        val registry = BleLinkRegistry()
        val gate = HandshakeRadioGate()
        val blocked = registry.begin("C-central", BleLinkRole.SERVER, null, ConcurrentLinkedDeque(), AtomicBoolean())
        val b = registry.begin("B", BleLinkRole.CLIENT, "B1", ConcurrentLinkedDeque(), AtomicBoolean())
        gate.begin(blocked.handshakeOwner)
        gate.begin(b.handshakeOwner)
        val captured = listOf(blocked)
        assertTrue(registry.ownsEndpoint("C-central", captured))
        assertTrue(registry.retire(blocked) { gate.finish(it) })
        assertTrue(registry.isCurrent(b))
        assertTrue(gate.isActive())
        assertEquals(b.handshakeOwner, gate.activeOwnerInfo().first)
        val replacement = registry.begin("C-central", BleLinkRole.SERVER, "B1", ConcurrentLinkedDeque(), AtomicBoolean())
        gate.begin(replacement.handshakeOwner)
        assertFalse(registry.ownsEndpoint("C-central", captured))
        assertFalse(registry.retire(blocked) { gate.finish(it) })
        assertTrue(registry.isCurrent(replacement))
    }

    @Test fun refreshRemovesOrphanGateOwnerButPreservesActiveReplacementSetup() {
        val gate = HandshakeRadioGate()
        gate.begin("old")
        gate.begin("replacement")
        assertFalse(gate.reconcileOwners { it == "replacement" })
        assertTrue(gate.isActive())
        assertFalse(gate.finish("old"))
        assertTrue(gate.reconcileOwners { false })
        assertFalse(gate.isActive())
    }

    @Test fun refreshCannotRemoveHandshakeAcquiredDuringReconciliation() {
        val gate = HandshakeRadioGate()
        gate.begin("orphan")
        assertFalse(gate.reconcileOwners {
            gate.begin("new-generation")
            false
        })
        assertTrue(gate.isActive())
        assertEquals("new-generation", gate.activeOwnerInfo().first)
    }

    @Test fun onlyIdentifiedUnblockedReadyNeighborsCanSuppressRecovery() {
        assertFalse(BleDirectPeerPolicy.isUsable(true, "Unknown Node", null, false))
        assertFalse(BleDirectPeerPolicy.isUsable(true, "C#C1", "C1", true))
        assertFalse(BleDirectPeerPolicy.isUsable(false, "B#B1", "B1", false))
        assertTrue(BleDirectPeerPolicy.isUsable(true, "B#B1", "B1", false))
    }

    @Test fun unknownConnectionsCountSeparatelyUntilStableIdentityIsBound() {
        assertNotEquals(BleDirectPeerPolicy.capacityKey("Unknown Node", null, "first"),
            BleDirectPeerPolicy.capacityKey("Unknown Node", null, "second"))
        assertEquals(BleDirectPeerPolicy.capacityKey("Bob#B1", "B1", "central"),
            BleDirectPeerPolicy.capacityKey("Bob#B1", "B1", "advertiser"))
    }

    @Test fun acknowledgedVoiceProgressSurvivesOldProbeButStopsProtectingAStall() {
        assertTrue(BleLivenessPolicy.isStale(1_000L, 40_000L))
        assertTrue(BleLivenessPolicy.hasRecentOutboundProgress(39_000L, 40_000L))
        assertFalse(BleLivenessPolicy.hasRecentOutboundProgress(39_000L, 47_000L))
        assertFalse(BleLivenessPolicy.hasRecentOutboundProgress(0L, 40_000L))
    }
}
