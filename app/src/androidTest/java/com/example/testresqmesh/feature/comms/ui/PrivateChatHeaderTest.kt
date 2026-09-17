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

class PrivateChatHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun header_showsTruthfulStatusAndActions() {
        var backs = 0
        var deletes = 0
        composeRule.setContent {
            TestResQMeshTheme {
                PrivateChatHeader(
                    name = "Ari",
                    availability = RecipientAvailability.Relayed,
                    onBack = { backs += 1 },
                    onDelete = { deletes += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Ari").assertIsDisplayed()
        composeRule.onNodeWithText("Relayed").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Delete conversation").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, deletes)
        }
    }
}
