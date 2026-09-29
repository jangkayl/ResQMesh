package com.example.testresqmesh.feature.setup.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FirstLaunchGuideScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun guideMovesForwardAndBackAndCanBeSkipped() {
        var closes = 0
        composeRule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                FirstLaunchGuideScreen(isReplay = false, onDone = {}, onClose = { closes++ })
            }
        }
        composeRule.onNodeWithText("When networks go quiet").assertIsDisplayed()
        composeRule.onNodeWithText("1 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Keep the conversation moving").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Ask for help with SOS").assertIsDisplayed()
        composeRule.onNodeWithText("Skip").performClick()
        composeRule.runOnIdle { assertEquals(1, closes) }
    }

    @Test
    fun replayCompletesOnLastPage() {
        var finishes = 0
        composeRule.setContent {
            TestResQMeshTheme(darkTheme = true) {
                FirstLaunchGuideScreen(isReplay = true, onDone = { finishes++ }, onClose = {})
            }
        }
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Ask for help with SOS").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.runOnIdle { assertEquals(1, finishes) }
    }
}
