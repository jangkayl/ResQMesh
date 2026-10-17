package com.example.testresqmesh.data.mapper

import com.example.testresqmesh.core.model.ConversationState
import com.example.testresqmesh.data.local.entity.ConversationStateEntity

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
