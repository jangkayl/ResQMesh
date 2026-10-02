package com.example.testresqmesh.feature.comms.model

import com.example.testresqmesh.core.model.ConnectedDevice
import org.junit.Assert.*
import org.junit.Test

class SosMeshStatusTest {
    private fun peer(endpoint: String = "one", id: String = "A", ready: Boolean = true,
                     provisional: Boolean = false, responsive: Boolean = true) =
        ConnectedDevice(endpoint, "Peer#$id", nodeId = id, isPayloadReady = ready,
            isProvisional = provisional, isPeerResponsive = responsive)

    @Test fun offlineNeverClaimsConnectedEvenWithRetainedPeers() {
        assertEquals(SosMeshState.OFF, sosMeshStatus(false, listOf(peer()), emptySet()).state)
    }

    @Test fun unidentifiedOrNonReadyLinksDoNotClaimConnectivity() {
        assertEquals(SosMeshState.SEARCHING, sosMeshStatus(true,
            listOf(peer(ready = false), peer(provisional = true)), emptySet()).state)
    }

    @Test fun blockedStableIdentityDoesNotClaimConnectivity() {
        assertEquals(SosMeshState.SEARCHING,
            sosMeshStatus(true, listOf(peer()), setOf("Renamed peer#A")).state)
    }

    @Test fun readyButUnresponsiveLinkIsChecking() {
        assertEquals(SosMeshState.CHECKING,
            sosMeshStatus(true, listOf(peer(responsive = false)), emptySet()).state)
    }

    @Test fun connectionCountUsesStableIdentityAcrossEndpoints() {
        val status = sosMeshStatus(true, listOf(peer(), peer(endpoint = "two"),
            peer(endpoint = "three", id = "B")), emptySet())
        assertEquals(SosMeshState.CONNECTED, status.state)
        assertEquals(2, status.readyPeers)
    }

    @Test fun transportAcceptanceDoesNotClaimRemoteConfirmation() {
        assertEquals("Saved · Waiting to send", sosTransmissionLabel("QUEUED"))
        assertEquals("Sent to a link · Remote confirmation pending", sosTransmissionLabel("SENT"))
        assertEquals("State confirmed by Peer", sosTransmissionLabel("CONFIRMED:Peer#A"))
        assertEquals("Received SOS state", sosTransmissionLabel("RECEIVED"))
    }
}
