package com.example.testresqmesh.feature.comms.ui

import com.example.testresqmesh.core.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryFeedbackTest {

    @Test
    fun feedback_doesNotClaimDeliveryWithoutEvidence() {
        assertEquals(DeliveryFeedback.Sent, deliveryFeedback(message()))
        assertEquals(DeliveryFeedback.Delivered, deliveryFeedback(message(deliveredTo = listOf("Ari"))))
        assertEquals(DeliveryFeedback.Read, deliveryFeedback(message(seenBy = listOf("Ari"))))
    }

    private fun message(
        deliveredTo: List<String> = emptyList(),
        seenBy: List<String> = emptyList()
    ) = ChatMessage(
        id = "message",
        senderName = "Me",
        text = "Hello",
        imageBase64 = null,
        audioBase64 = null,
        isMine = true,
        deliveredTo = deliveredTo,
        seenBy = seenBy
    )
}
