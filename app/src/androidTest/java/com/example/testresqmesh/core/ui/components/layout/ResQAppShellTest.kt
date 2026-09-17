package com.example.testresqmesh.core.ui.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResQAppShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shell_exposesThreeDestinations_andDispatchesSelection() {
        var selected = ResQDestination.Home

        composeRule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                ResQAppShell(
                    selectedDestination = selected,
                    onDestinationSelected = { selected = it },
                    onSosActivated = {},
                    content = { Box {} }
                )
            }
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Messages").performClick()
        composeRule.onNodeWithText("Network").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(ResQDestination.Messages, selected)
        }
    }

    @Test
    fun sos_exposesAccessibleLongClickAction() {
        var activated = false

        composeRule.setContent {
            TestResQMeshTheme(darkTheme = true) {
                ResQAppShell(
                    selectedDestination = ResQDestination.Home,
                    onDestinationSelected = {},
                    onSosActivated = { activated = true },
                    content = { Box {} }
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Press and hold for two seconds to activate SOS")
            )
        ).performSemanticsAction(SemanticsActions.OnLongClick)

        composeRule.runOnIdle {
            assertTrue(activated)
        }
    }
}
