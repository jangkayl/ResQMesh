package com.example.testresqmesh.feature.comms.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.ui.state.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NewMessageModalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_isClearAndRefreshable() {
        var refreshes = 0
        composeRule.setContent {
            TestResQMeshTheme {
                NewMessageModal(
                    uiState = ChatUiState(),
                    onDismiss = {},
                    onRefresh = { refreshes += 1 },
                    onNodeSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("No people found").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh people").performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun directRecipient_canBeSelected() {
        var selected = ""
        composeRule.setContent {
            TestResQMeshTheme {
                NewMessageModal(
                    uiState = ChatUiState(
                        connectedDevices = listOf(
                            ConnectedDevice(
                                endpointId = "ari",
                                name = "Ari [NODE]#A1",
                                isPayloadReady = true,
                                isPeerResponsive = true
                            )
                        )
                    ),
                    onDismiss = {},
                    onRefresh = {},
                    onNodeSelected = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Direct").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Choose Ari").performClick()
        composeRule.runOnIdle { assertEquals("Ari [NODE]#A1", selected) }
    }
}
