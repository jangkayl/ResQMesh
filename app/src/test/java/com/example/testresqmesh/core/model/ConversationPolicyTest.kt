package com.example.testresqmesh.core.model

import org.junit.Assert.*
import org.junit.Test

class ConversationPolicyTest {
    private fun message(kind: String, channel: String = "", sos: String = "", private: Boolean = false) =
        ChatMessage(kind + channel + sos, "Peer", "text", null, "audio", isMine = false,
            isPrivate = private, conversationKind = kind, channelId = channel, sosId = sos)
    @Test fun feedsAndKeysSeparateChannelsAndSosThreads() {
        val all = listOf(message("COMMUNITY"), message("RADIO", "1"), message("RADIO", "2"),
            message("SOS", sos = "A"), message("SOS", sos = "B"))
        assertEquals(listOf(all[0]), ConversationPolicy.messages(all, "COMMUNITY"))
        assertEquals(listOf(all[1]), ConversationPolicy.messages(all, "RADIO", "1"))
        assertEquals(listOf(all[3]), ConversationPolicy.messages(all, "SOS", sosId = "A"))
        assertNotEquals(ConversationPolicy.key("RADIO", "1"), ConversationPolicy.key("RADIO", "2"))
        assertNotEquals(ConversationPolicy.key("SOS", sosId = "A"), ConversationPolicy.key("SOS", sosId = "B"))
    }
    @Test fun autoplayExcludesPrivateLegacyCommunityOtherChannelsAndMutedMonitoring() {
        assertTrue(ConversationPolicy.autoplay(message("RADIO", "1"), "1", true))
        assertFalse(ConversationPolicy.autoplay(message("RADIO", "2"), "1", true))
        assertFalse(ConversationPolicy.autoplay(message("RADIO", "1", private = true), "1", true))
        assertFalse(ConversationPolicy.autoplay(message("LEGACY_RADIO"), "1", true))
        assertFalse(ConversationPolicy.autoplay(message("COMMUNITY"), "1", true))
        assertFalse(ConversationPolicy.autoplay(message("RADIO", "1"), "1", false))
    }
}
