package com.example.testresqmesh.feature.setup.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Rule
import org.junit.Test

class SplashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun splash_explainsOfflinePurpose_andExposesLoadingState() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                SplashScreen(onTimeout = {})
            }
        }

        composeRule.onNodeWithText("ResQMesh").assertIsDisplayed()
        composeRule.onNodeWithText("Stay connected when networks fail.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Preparing ResQMesh").assertIsDisplayed()
    }
}
