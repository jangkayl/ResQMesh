package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.ui.state.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationPreviewsTest {

    @Test
    fun currentShorterNameUpdatesInboxWithoutChangingConversationIdentity() {
        val oldName = "Alexander#A1"
        val storedMessage = message("old", oldName, 1L)
        val state = ChatUiState(
            privateMessages = mapOf(oldName to listOf(storedMessage)),
            peerNames = mapOf("A1" to "Alex#A1")
        )

        val preview = conversationPreviews(state).single()
        assertEquals(oldName, preview.id)
        assertEquals("Alex", preview.displayName)
        assertEquals(oldName, preview.lastMessage.senderName)
    }

    @Test
    fun identicalHumanNamesRemainSeparateByStableId() {
        val state = ChatUiState(
            privateMessages = mapOf(
                "Old A#A1" to listOf(message("a", "Old A#A1", 1L)),
                "Old B#B2" to listOf(message("b", "Old B#B2", 2L))
            ),
            peerNames = mapOf("A1" to "Alex#A1", "B2" to "Alex#B2")
        )

        assertEquals(2, conversationPreviews(state).size)
        assertEquals(setOf("Alex"), conversationPreviews(state).map { it.displayName }.toSet())
    }

    @Test
    fun previews_usePayloadReadinessForDirectStatus() {
        val ari = "Ari [NODE]#A1"
        val bea = "Bea [NODE]#B2"
        val cai = "Cai [NODE]#C3"
        val state = ChatUiState(
            privateMessages = mapOf(
                ari to listOf(message("ari", ari, 1L)),
                bea to listOf(message("bea", bea, 2L)),
                cai to listOf(message("cai", cai, 3L))
            ),
            connectedDevices = listOf(
                ConnectedDevice("ari", ari, isPayloadReady = true, isPeerResponsive = true),
                ConnectedDevice("bea", bea, isPayloadReady = true, isPeerResponsive = false)
            ),
            knownNodes = listOf(KnownNode(cai, isDirect = false, lastSeen = 0L))
        )

        val previews = conversationPreviews(state).associateBy { it.id }

        assertEquals(ConversationStatus.Direct, previews.getValue(ari).status)
        assertEquals(ConversationStatus.Checking, previews.getValue(bea).status)
        assertEquals(ConversationStatus.Relayed, previews.getValue(cai).status)
    }

    private fun message(id: String, sender: String, timestamp: Long) = ChatMessage(
        id = id,
        senderName = sender,
        text = "Hi",
        imageBase64 = null,
        audioBase64 = null,
        isMine = false,
        isPrivate = true,
        timestamp = timestamp
    )
}
