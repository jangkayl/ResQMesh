package edu.cit.resqmesh.data.repository.adapter

import edu.cit.resqmesh.core.domain.repository.ConversationStateRepository
import edu.cit.resqmesh.core.model.ConversationState
import edu.cit.resqmesh.data.local.dao.ConversationStateDao
import edu.cit.resqmesh.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationStateRepositoryAdapter(private val dao: ConversationStateDao) : ConversationStateRepository {
    override fun observe(): Flow<List<ConversationState>> = dao.observe().map { list -> list.map { it.toModel() } }
    override suspend fun saveDraft(conversationId: String, draft: String) { dao.draft(conversationId, draft) }
    override suspend fun markRead(conversationId: String, time: Long) { dao.read(conversationId, time) }
}
