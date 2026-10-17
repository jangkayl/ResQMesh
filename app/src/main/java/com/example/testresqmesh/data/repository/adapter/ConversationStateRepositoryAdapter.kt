package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.domain.repository.ConversationStateRepository
import com.example.testresqmesh.core.model.ConversationState
import com.example.testresqmesh.data.local.dao.ConversationStateDao
import com.example.testresqmesh.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationStateRepositoryAdapter(private val dao: ConversationStateDao) : ConversationStateRepository {
    override fun observe(): Flow<List<ConversationState>> = dao.observe().map { list -> list.map { it.toModel() } }
    override suspend fun saveDraft(conversationId: String, draft: String) { dao.draft(conversationId, draft) }
    override suspend fun markRead(conversationId: String, time: Long) { dao.read(conversationId, time) }
}
