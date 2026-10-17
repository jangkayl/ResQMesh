package edu.cit.resqmesh.core.model

/** Mirrors ConversationStateEntity 1:1. */
data class ConversationState(
    val conversationId: String,
    val draft: String = "",
    val lastReadAt: Long = 0L
)
