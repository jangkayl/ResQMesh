package com.example.testresqmesh.core.model

object ConversationPolicy {
    fun key(kind: String, channel: String = "", sosId: String = "") = when (kind) {
        "RADIO" -> "RADIO:$channel"
        "SOS" -> "SOS:$sosId"
        else -> kind
    }
    fun autoplay(message: ChatMessage, selectedChannel: String, monitoring: Boolean) =
        monitoring && !message.isPrivate && !message.isMine && message.conversationKind == "RADIO" &&
            message.channelId == selectedChannel && message.audioBase64 != null
    fun messages(all: List<ChatMessage>, kind: String, channel: String = "", sosId: String = "") =
        all.filter { it.conversationKind == kind &&
            (kind != "RADIO" || it.channelId == channel) && (kind != "SOS" || it.sosId == sosId) }
}
