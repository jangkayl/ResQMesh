package com.example.testresqmesh.feature.setup.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingPermissionState_explainsAccess_andRequestsPermissions() {
        var requests = 0

        composeRule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                PermissionsScreen(
                    onAllSet = {},
                    hasPermissions = false,
                    requestPermissions = { requests += 1 },
                    checkHardware = { false }
                )
            }
        }

        composeRule.onNodeWithText("Allow essential access").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby devices").assertIsDisplayed()
        composeRule.onNodeWithText("Allow access").performClick()

        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun missingHardwareState_explainsRecovery() {
        composeRule.setContent {
            TestResQMeshTheme(darkTheme = true) {
                PermissionsScreen(
                    onAllSet = {},
                    hasPermissions = true,
                    requestPermissions = {},
                    checkHardware = { false }
                )
            }
        }

        composeRule.onNodeWithText("Turn on Bluetooth and Location").assertIsDisplayed()
        composeRule.onNodeWithText("Phone settings need attention").assertIsDisplayed()
        composeRule.onNodeWithText("Check again").assertIsDisplayed()
    }
}
