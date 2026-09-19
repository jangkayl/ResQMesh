package com.example.testresqmesh.feature.home.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
                    onMessagesClick = {},
                    onNetworkClick = {},
                    onProfileClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Nearby sharing is on").assertIsDisplayed()
        composeRule.onNodeWithText("1 direct").assertIsDisplayed()
        composeRule.onNodeWithText("The SOS action stays ready in the navigation dock.").assertIsDisplayed()
    }

    @Test
    fun home_actions_openTheirDestinations() {
        var messagesOpened = 0
        var networkOpened = 0
        var profileOpened = 0

        composeRule.setContent {
            TestResQMeshTheme {
                HomeScreenContent(
                    isNodeActive = false,
                    summary = HomeNetworkSummary(0, 0, 0, 0),
                    onMessagesClick = { messagesOpened += 1 },
                    onNetworkClick = { networkOpened += 1 },
                    onProfileClick = { profileOpened += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Start a conversation").performClick()
        composeRule.onNodeWithText("Your network").performClick()
        composeRule.onNodeWithContentDescription("Open profile").performClick()

        composeRule.runOnIdle {
            assertEquals(1, messagesOpened)
            assertEquals(1, networkOpened)
            assertEquals(1, profileOpened)
        }
    }
}
