package com.example.testresqmesh.feature.comms.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CommunityHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun header_showsPublicContextAndBackAction() {
        var backs = 0
        composeRule.setContent {
            TestResQMeshTheme {
                CommunityHeader(
                    channelId = "2",
                    onBack = { backs += 1 },
                    onChannelSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Community").assertIsDisplayed()
        composeRule.onNodeWithText("Public messages").assertIsDisplayed()
        composeRule.onNodeWithText("Ch 2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to messages").performClick()
        composeRule.runOnIdle { assertEquals(1, backs) }
    }
}
