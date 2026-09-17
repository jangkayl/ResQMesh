package com.example.testresqmesh.feature.comms.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessagesInboxScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyInbox_showsCommunityAndHelpfulEmptyState() {
        composeRule.setContent {
            TestResQMeshTheme {
                MessagesInboxContent(
                    channelId = "1",
                    communityPreview = null,
                    conversations = emptyList(),
                    onNewMessageClick = {},
                    onCommunityClick = {},
                    onChannelSelected = {},
                    onConversationClick = {},
                    onConversationSeen = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Community").assertIsDisplayed()
        composeRule.onNodeWithText("Private").assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test
    fun inbox_actions_openTheirFlows() {
        var newMessageOpened = 0
        var communityOpened = 0
        var chatOpened = ""
        val conversation = ConversationPreview(
            id = "Ari [NODE]#A1",
            displayName = "Ari",
            initial = "A",
            preview = "I am here.",
            timeLabel = "Now",
            status = ConversationStatus.Direct,
            lastMessage = message()
        )

        composeRule.setContent {
            TestResQMeshTheme {
                MessagesInboxContent(
                    channelId = "1",
                    communityPreview = null,
                    conversations = listOf(conversation),
                    onNewMessageClick = { newMessageOpened += 1 },
                    onCommunityClick = { communityOpened += 1 },
                    onChannelSelected = {},
                    onConversationClick = { chatOpened = it },
                    onConversationSeen = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("New message").performClick()
        composeRule.onNodeWithContentDescription("Open community").performClick()
        composeRule.onNodeWithContentDescription("Open chat with Ari").performClick()

        composeRule.runOnIdle {
            assertEquals(1, newMessageOpened)
            assertEquals(1, communityOpened)
            assertEquals("Ari [NODE]#A1", chatOpened)
        }
    }

    private fun message() = ChatMessage(
        id = "message-1",
        senderName = "Ari [NODE]#A1",
        text = "I am here.",
        imageBase64 = null,
        audioBase64 = null,
        isMine = false
    )
}
