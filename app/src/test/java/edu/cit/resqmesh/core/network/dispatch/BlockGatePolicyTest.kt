package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.network.MeshPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockGatePolicyTest {
    private val blockedC: (String) -> Boolean = { it.endsWith("C003") }

    @Test fun pulseIsHeldOnlyBeforeTheTeardownDeadline() {
        assertTrue(BlockGatePolicy.holdsPulse(deadline = 5_000L, now = 4_999L))
        assertFalse(BlockGatePolicy.holdsPulse(deadline = 5_000L, now = 5_000L))
        assertFalse(BlockGatePolicy.holdsPulse(deadline = null, now = 0L))
    }

    @Test fun framesOnABlockedLinkAreDroppedExceptEncryptedBlockControl() {
        val text = MeshPayload(id = "m", type = "MESSAGE", senderName = "Carl#C003")
        assertTrue(BlockGatePolicy.dropsFrame("Carl#C003", blockedC, text))
        val ack = MeshPayload(id = "a", type = "BLOCK_ACK", senderName = "Carl#C003", isPrivate = true,
            isEncrypted = true, encryptedData = "d", encryptedKey = "k")
        assertFalse(BlockGatePolicy.dropsFrame("Carl#C003", blockedC, ack))
        assertTrue(BlockGatePolicy.dropsFrame("Carl#C003", blockedC, ack.copy(isEncrypted = false)))
    }

    @Test fun relayedTrafficFromABlockedOriginViaAnUnblockedNeighbourPasses() {
        val relayed = MeshPayload(id = "r", type = "MESSAGE", senderName = "Carl#C003", routePath = listOf("Bea#B002"))
        assertFalse(BlockGatePolicy.dropsFrame("Bea#B002", blockedC, relayed))
    }

    @Test fun placeholderOrUnnamedLinksAreNeverTreatedAsBlocked() {
        val text = MeshPayload(id = "m", type = "MESSAGE")
        val everything: (String) -> Boolean = { true }
        assertFalse(BlockGatePolicy.dropsFrame(null, everything, text))
        assertFalse(BlockGatePolicy.dropsFrame("Unknown Node", everything, text))
        assertFalse(BlockGatePolicy.dropsFrame("", everything, text))
    }

    @Test fun bootstrapPulsesStillReachTheIdentityGate() {
        val pulse = MeshPayload(id = "s", type = "SYSTEM", senderName = "Carl#C003")
        assertFalse(BlockGatePolicy.dropsFrame("Carl#C003", blockedC, pulse))
    }
}
