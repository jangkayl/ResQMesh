package com.example.testresqmesh.feature.home.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.model.RadarUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.example.testresqmesh.feature.home.model.HomeNetworkSummary

class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activeHome_showsReadinessAndNetworkSummary() {
        composeRule.setContent {
            TestResQMeshTheme {
                HomeScreenContent(
                    isNodeActive = true,
                    summary = HomeNetworkSummary(
                        directPeers = 1,
                        relayedPeers = 0,
                        nearbyPeers = 2,
                        checkingPeers = 0
                    ),
                    radarState = RadarUiState(),
                    myDeviceName = "ALPHA-NODE",
                    directNodeNames = emptyList(),
                    onMessagesClick = {},
                    onNetworkClick = {},
                    onProfileClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Nearby sharing is on").assertIsDisplayed()
        composeRule.onNodeWithText("1 direct").assertIsDisplayed()
        composeRule.onNodeWithText("The SOS action stays ready in the navigation dock.").assertIsDisplayed()
        composeRule.onNodeWithText("Location found").assertIsDisplayed()
        composeRule.onAllNodesWithText("MESH ACTIVE").assertCountEquals(0)
        composeRule.onAllNodesWithText("Start a conversation").assertCountEquals(0)
        composeRule.onAllNodesWithText("AES-GCM").assertCountEquals(0)
    }

    @Test
    fun home_actions_openNetworkProfileAndIncidents() {
        var networkOpened = 0
        var profileOpened = 0
        var incidentsOpened = 0

        composeRule.setContent {
            TestResQMeshTheme {
                HomeScreenContent(
                    isNodeActive = false,
                    summary = HomeNetworkSummary(0, 0, 0, 0),
                    radarState = RadarUiState(),
                    myDeviceName = "ALPHA-NODE",
                    directNodeNames = emptyList(),
                    onMessagesClick = {},
                    onNetworkClick = { networkOpened += 1 },
                    onProfileClick = { profileOpened += 1 },
                    onIncidentsClick = { incidentsOpened += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Your network").performClick()
        composeRule.onNodeWithText("EMERGENCY INCIDENTS").performClick()
        composeRule.onNodeWithContentDescription("Open profile").performClick()

        composeRule.runOnIdle {
            assertEquals(1, networkOpened)
            assertEquals(1, profileOpened)
            assertEquals(1, incidentsOpened)
        }
    }

    @Test
    fun linkedPeerChip_passesSelectedPeerToDetailsAction() {
        var selectedPeer: String? = null
        composeRule.setContent {
            TestResQMeshTheme {
                HomeScreenContent(
                    isNodeActive = true,
                    summary = HomeNetworkSummary(1, 0, 0, 0),
                    radarState = RadarUiState(),
                    myDeviceName = "Alpha#B2",
                    directNodeNames = listOf("Ari#A1"),
                    onMessagesClick = {},
                    onNetworkClick = {},
                    onPeerClick = { selectedPeer = it },
                    onProfileClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Ari#A1").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("Ari#A1", selectedPeer) }
    }
}
