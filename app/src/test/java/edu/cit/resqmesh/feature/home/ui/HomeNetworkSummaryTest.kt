package edu.cit.resqmesh.feature.home.ui

import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.KnownNode
import edu.cit.resqmesh.core.model.ScannedDevice
import edu.cit.resqmesh.core.ui.model.RadarUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import edu.cit.resqmesh.feature.home.model.homeNetworkSummary

class HomeNetworkSummaryTest {

    @Test
    fun summary_countsOnlyReadyResponsiveLinksAsDirect() {
        val summary = homeNetworkSummary(
            RadarUiState(
                connectedDevices = listOf(
                    ConnectedDevice(
                        endpointId = "direct",
                        name = "Ari [NODE]#A1",
                        isPayloadReady = true,
                        isPeerResponsive = true
                    ),
                    ConnectedDevice(
                        endpointId = "checking",
                        name = "Bea [NODE]#B2",
                        isPayloadReady = true,
                        isPeerResponsive = false
                    ),
                    ConnectedDevice(
                        endpointId = "setup",
                        name = "pending",
                        isPayloadReady = false,
                        isProvisional = true
                    )
                ),
                knownNodes = listOf(KnownNode("Cai [NODE]#C3", isDirect = false, lastSeen = 0L)),
                scannedDevices = listOf(
                    ScannedDevice("direct", "Ari [NODE]#A1", 0L),
                    ScannedDevice("checking", "Bea [NODE]#B2", 0L),
                    ScannedDevice("relay", "Cai [NODE]#C3", 0L),
                    ScannedDevice("nearby", "Dee [NODE]#D4", 0L)
                )
            )
        )

        assertEquals(1, summary.directPeers)
        assertEquals(1, summary.checkingPeers)
        assertEquals(1, summary.relayedPeers)
        assertEquals(1, summary.nearbyPeers)
    }
}
