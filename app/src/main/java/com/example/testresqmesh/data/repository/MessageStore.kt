package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.entity.toMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MessageStore {
    val publicMessages: Flow<List<ChatMessage>>
    val privateMessages: Flow<Map<String, List<ChatMessage>>>
    suspend fun save(message: ChatMessage, targetName: String?)
    suspend fun markDelivered(messageId: String, readerName: String)
    suspend fun markSeen(messageId: String, readerName: String)
    suspend fun markFailed(messageId: String)
    suspend fun markPending(messageId: String)
    suspend fun markSent(messageId: String)
    suspend fun getPendingOutbox(minTimestamp: Long): List<Pair<ChatMessage, String?>>
    suspend fun deleteConversation(peerName: String)
}

class RoomMessageStore(private val dao: MessageDao) : MessageStore {
    override val publicMessages: Flow<List<ChatMessage>> = dao.getPublicMessages().map { messages ->
        messages.map { it.toChatMessage() }
    }

    override val privateMessages: Flow<Map<String, List<ChatMessage>>> =
        dao.getAllPrivateMessages().map { messages ->
            messages.groupBy {
                if (it.isMine) it.targetName ?: it.senderName else it.senderName
            }.mapValues { entry -> entry.value.map { it.toChatMessage() } }
        }

    override suspend fun save(message: ChatMessage, targetName: String?) {
        dao.insertMessage(message.toMessageEntity(targetName))
    }

    override suspend fun markDelivered(messageId: String, readerName: String) {
        val message = dao.getMessageById(messageId) ?: return
        val readers = message.deliveredTo.split(',').filter { it.isNotEmpty() && it != "FAILED" && it != "PENDING" }
        if (readerName in readers) return
        dao.updateDeliveredTo(messageId, (readers + readerName).joinToString(","))
    }

    override suspend fun markSeen(messageId: String, readerName: String) {
        val message = dao.getMessageById(messageId) ?: return
        val readers = message.seenBy.split(',').filter { it.isNotEmpty() && it != "FAILED" && it != "PENDING" }
        if (readerName in readers) return
        dao.updateSeenBy(messageId, (readers + readerName).joinToString(","))
    }

    override suspend fun markFailed(messageId: String) {
        val message = dao.getMessageById(messageId) ?: return
        if (message.deliveredTo.isNotEmpty() && message.deliveredTo != "PENDING") return
        if (message.seenBy.isNotEmpty()) return
        dao.updateDeliveredTo(messageId, "FAILED")
    }

    override suspend fun markPending(messageId: String) {
        val message = dao.getMessageById(messageId) ?: return
        if (message.deliveredTo.isNotEmpty() && message.deliveredTo != "PENDING" && message.deliveredTo != "FAILED") return
        if (message.seenBy.isNotEmpty()) return
        dao.updateDeliveredTo(messageId, "PENDING")
    }

    override suspend fun markSent(messageId: String) {
        val message = dao.getMessageById(messageId) ?: return
        if (message.deliveredTo == "PENDING" || message.deliveredTo == "FAILED") {
            dao.updateDeliveredTo(messageId, "")
        }
    }

    override suspend fun getPendingOutbox(minTimestamp: Long): List<Pair<ChatMessage, String?>> {
        return dao.getPendingOutboxMessages(minTimestamp).map {
            Pair(it.toChatMessage(), it.targetName)
        }
    }

    override suspend fun deleteConversation(peerName: String) {
        dao.deleteConversationWith(peerName)
    }
}
