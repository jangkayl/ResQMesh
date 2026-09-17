package com.example.testresqmesh.feature.home.ui

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.ui.state.RadarUiState
import org.junit.Assert.assertEquals
import org.junit.Test

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
