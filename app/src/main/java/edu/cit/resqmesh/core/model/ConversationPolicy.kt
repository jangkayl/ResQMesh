package edu.cit.resqmesh.core.model

object ConversationPolicy {
    /** Generous for slow multi-hop notes and phone clock drift; late relay copies can be 10 minutes old. */
    const val MAX_AUTOPLAY_AGE_MS = 2 * 60 * 1000L

    fun key(kind: String, channel: String = "", sosId: String = "") = when (kind) {
        "RADIO" -> "RADIO:$channel"
        "SOS" -> "SOS:$sosId"
        else -> kind
    }
    fun autoplay(message: ChatMessage, selectedChannel: String, monitoring: Boolean,
        nowMs: Long = System.currentTimeMillis()) =
        monitoring && !message.isPrivate && !message.isMine && message.conversationKind == "RADIO" &&
            message.channelId == selectedChannel && message.audioBase64 != null &&
            nowMs - message.timestamp <= MAX_AUTOPLAY_AGE_MS
    fun messages(all: List<ChatMessage>, kind: String, channel: String = "", sosId: String = "") =
        all.filter { it.conversationKind == kind &&
            (kind != "RADIO" || it.channelId == channel) && (kind != "SOS" || it.sosId == sosId) }
}
