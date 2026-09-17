package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.ui.state.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientCandidatesTest {

    @Test
    fun candidates_reflectConnectionAndRoutingEvidence() {
        val direct = "Ari [NODE]#A1"
        val checking = "Bea [NODE]#B2"
        val relayed = "Cai [NODE]#C3"
        val nearby = "Dee [NODE]#D4"
        val connecting = "Eli [NODE]#E5"
        val offline = "Fae [NODE]#F6"
        val blocked = "Gia [NODE]#G7"
        val candidates = recipientCandidates(
            ChatUiState(
                connectedDevices = listOf(
                    ConnectedDevice("direct", direct, isPayloadReady = true, isPeerResponsive = true),
                    ConnectedDevice("checking", checking, isPayloadReady = true, isPeerResponsive = false)
                ),
                knownNodes = listOf(
                    KnownNode(relayed, isDirect = false, lastSeen = 0L),
                    KnownNode(offline, isDirect = true, lastSeen = 0L),
                    KnownNode(blocked, isDirect = false, lastSeen = 0L)
                ),
                scannedDevices = listOf(
                    ScannedDevice("nearby", nearby, 0L),
                    ScannedDevice("connecting", connecting, 0L, isConnecting = true)
                ),
                blockedDeviceNames = setOf(blocked)
            )
        ).associateBy { it.name }

        assertEquals(RecipientAvailability.Direct, candidates.getValue(direct).availability)
        assertEquals(RecipientAvailability.Checking, candidates.getValue(checking).availability)
        assertEquals(RecipientAvailability.Relayed, candidates.getValue(relayed).availability)
        assertEquals(RecipientAvailability.Nearby, candidates.getValue(nearby).availability)
        assertEquals(RecipientAvailability.Connecting, candidates.getValue(connecting).availability)
        assertEquals(RecipientAvailability.Offline, candidates.getValue(offline).availability)
        assertEquals(RecipientAvailability.Blocked, candidates.getValue(blocked).availability)
    }
}
