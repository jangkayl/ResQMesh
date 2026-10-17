package edu.cit.resqmesh.core.domain.repository

import edu.cit.resqmesh.core.model.ConversationState
import kotlinx.coroutines.flow.Flow

/** Per-conversation draft text and last-read marker. */
interface ConversationStateRepository {
    fun observe(): Flow<List<ConversationState>>
    suspend fun saveDraft(conversationId: String, draft: String)
    suspend fun markRead(conversationId: String, time: Long)
}
