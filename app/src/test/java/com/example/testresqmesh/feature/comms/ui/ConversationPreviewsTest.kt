package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.ui.state.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationPreviewsTest {

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
