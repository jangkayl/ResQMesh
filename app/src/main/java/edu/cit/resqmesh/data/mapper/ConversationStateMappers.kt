package edu.cit.resqmesh.data.mapper

import edu.cit.resqmesh.core.model.ConversationState
import edu.cit.resqmesh.data.local.entity.ConversationStateEntity

fun ConversationStateEntity.toModel(): ConversationState = ConversationState(
    conversationId = conversationId,
    draft = draft,
    lastReadAt = lastReadAt
)

fun ConversationState.toEntity(): ConversationStateEntity = ConversationStateEntity(
    conversationId = conversationId,
    draft = draft,
    lastReadAt = lastReadAt
)
