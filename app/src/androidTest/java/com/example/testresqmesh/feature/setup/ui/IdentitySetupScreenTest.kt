package com.example.testresqmesh.feature.setup.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IdentitySetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blankName_showsHelpfulValidationMessage() {
        composeRule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                IdentitySetupContent(
                    customName = "",
                    onCustomNameChange = {},
                    nodeTag = "",
                    onNodeTagChange = {},
                    onIdentityGenerated = {}
                )
            }
        }

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Enter your name.").assertIsDisplayed()
    }

    @Test
    fun validName_startsSetup() {
        var starts = 0
        var name by mutableStateOf("Ari Santos")

        composeRule.setContent {
            TestResQMeshTheme(darkTheme = true) {
                IdentitySetupContent(
                    customName = name,
                    onCustomNameChange = { name = it },
                    nodeTag = "TEAM1",
                    onNodeTagChange = {},
                    onIdentityGenerated = { starts += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.runOnIdle { assertEquals(1, starts) }
    }
}
