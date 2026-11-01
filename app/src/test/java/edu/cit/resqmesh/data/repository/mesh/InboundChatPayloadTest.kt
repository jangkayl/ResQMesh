package edu.cit.resqmesh.data.repository.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class InboundChatPayloadTest {
    private fun payload(sender: String, text: String) = InboundChatPayload(
        endpointId = "e", msgId = "m1", sender = sender, text = text, isPrivate = false, isSystem = false,
        imageBase64 = null, audioBase64 = null, locationLat = null, locationLng = null, medium = "BLE",
        routePath = emptyList(), channelId = "1"
    )

    @Test
    fun invisibleOnlyTextGetsPlaceholderUnlessAttachment() {
        assertEquals("[Message unavailable]", inboundDisplayText("​‮", hasAttachment = false))
        assertEquals("", inboundDisplayText("​", hasAttachment = true))
        assertEquals("hi", inboundDisplayText("hi", hasAttachment = false))
        val message = payload("​#AB12", "​").toChatMessage(isDirect = true)
        assertEquals("Unknown Node#AB12", message.senderName)
        assertEquals("[Message unavailable]", message.text)
    }

    @Test
    fun storedCopyIsCleanedWhileWirePayloadStaysIdentical() {
        val raw = payload("Ann‮#AB12", "hi‮ there " + "x".repeat(3000))
        val before = raw.copy()
        val message = raw.toChatMessage(isDirect = true)
        assertEquals("Ann#AB12", message.senderName)
        assertEquals("AB12", message.senderNodeId)
        assertEquals(1000, message.text.length)
        assertEquals(false, message.text.contains('‮'))
        assertEquals(before, raw)
        assertEquals("Ann‮#AB12", raw.sender)
    }
}
