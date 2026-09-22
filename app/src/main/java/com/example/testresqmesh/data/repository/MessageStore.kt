package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.data.local.dao.MessageDao
import com.example.testresqmesh.data.local.entity.MessageEntity
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
    suspend fun getPendingOutbox(): List<Pair<ChatMessage, String?>>
    suspend fun deleteConversation(peerName: String)
}

class RoomMessageStore(private val dao: MessageDao) : MessageStore {
    override val publicMessages: Flow<List<ChatMessage>> = dao.getPublicMessages().map { messages ->
        messages.map { it.toChatMessage() }
    }

    override val privateMessages: Flow<Map<String, List<ChatMessage>>> =
        dao.getAllPrivateMessages().map(::groupPrivateMessages)

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

    override suspend fun getPendingOutbox(): List<Pair<ChatMessage, String?>> {
        return dao.getPendingOutboxMessages().map {
            Pair(it.toChatMessage(), it.targetName)
        }
    }

    override suspend fun deleteConversation(peerName: String) {
        val identityKey = NodeIdentity.key(peerName)
        val matchingIds = dao.getAllMessagesOnce()
            .asSequence()
            .filter { it.targetName != null }
            .filter { entity ->
                val storedPeer = if (entity.isMine) entity.targetName ?: entity.senderName else entity.senderName
                NodeIdentity.key(storedPeer) == identityKey
            }
            .map { it.msgId }
            .toList()
        if (matchingIds.isNotEmpty()) dao.deleteMessagesByIds(matchingIds)
    }
}

/** Collapses truncated and complete labels carrying the same stable node ID into one thread. */
internal fun groupPrivateMessages(messages: List<MessageEntity>): Map<String, List<ChatMessage>> =
    messages
        .groupBy { entity ->
            val peer = if (entity.isMine) entity.targetName ?: entity.senderName else entity.senderName
            NodeIdentity.key(peer)
        }
        .values
        .associate { entities ->
            val peerNames = entities.map { entity ->
                if (entity.isMine) entity.targetName ?: entity.senderName else entity.senderName
            }
            val canonicalPeer = NodeIdentity.preferredName(peerNames).ifBlank { peerNames.first() }
            canonicalPeer to entities.map { entity ->
                val message = entity.toChatMessage()
                if (message.isMine) message else message.copy(senderName = canonicalPeer)
            }
        }
