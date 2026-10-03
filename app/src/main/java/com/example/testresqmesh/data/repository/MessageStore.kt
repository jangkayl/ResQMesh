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
    val allPublicMessages: Flow<List<ChatMessage>> get() = publicMessages
    val privateMessages: Flow<Map<String, List<ChatMessage>>>
    suspend fun save(message: ChatMessage, targetName: String?)
    suspend fun contains(id: String): Boolean = false
    suspend fun incomingPrivateWasSeen(messageId: String): Boolean = false
    suspend fun recoverUnacknowledgedPrivateSends() {}
    suspend fun isUnacknowledgedPrivateSend(messageId: String): Boolean = false
    suspend fun saveIncomingPrivateIfAbsent(message: ChatMessage): PrivateMessageInsert {
        if (contains(message.id)) return PrivateMessageInsert.DUPLICATE
        save(message, message.senderName)
        return PrivateMessageInsert.INSERTED
    }
    suspend fun markDelivered(messageId: String, readerName: String)
    suspend fun markSeen(messageId: String, readerName: String)
    suspend fun markFailed(messageId: String)
    suspend fun expirePending(messageId: String)
    suspend fun markPending(messageId: String)
    suspend fun markSent(messageId: String)
    suspend fun getPendingOutbox(): List<Pair<ChatMessage, String?>>
    suspend fun deleteConversation(peerName: String)
}

enum class PrivateMessageInsert { INSERTED, DUPLICATE, REJECTED }

class RoomMessageStore(private val dao: MessageDao) : MessageStore {
    override suspend fun incomingPrivateWasSeen(messageId: String): Boolean =
        dao.getMessageById(messageId)?.let { !it.isMine && it.targetName != null && it.seenBy.isNotEmpty() } == true
    override suspend fun recoverUnacknowledgedPrivateSends() { dao.recoverUnacknowledgedPrivateSends() }
    override suspend fun isUnacknowledgedPrivateSend(messageId: String): Boolean =
        dao.getMessageById(messageId)?.let { it.isMine && it.targetName != null && it.deliveredTo.isEmpty() && it.seenBy.isEmpty() } == true
    override suspend fun saveIncomingPrivateIfAbsent(message: ChatMessage): PrivateMessageInsert =
        when (dao.insertIncomingPrivateIfAbsent(message.toMessageEntity(message.senderName))) {
            1 -> PrivateMessageInsert.INSERTED
            0 -> PrivateMessageInsert.DUPLICATE
            else -> PrivateMessageInsert.REJECTED
        }
    override suspend fun contains(id: String) = dao.getMessageById(id) != null
    override val allPublicMessages = dao.getAllPublicMessages().map { rows -> rows.map { it.toChatMessage() } }
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
        dao.failUnacknowledgedSend(messageId)
    }

    override suspend fun expirePending(messageId: String) {
        dao.expirePendingSend(messageId)
    }

    override suspend fun markPending(messageId: String) {
        dao.markPendingIfUnacknowledged(messageId)
    }

    override suspend fun markSent(messageId: String) {
        dao.markSentIfUnacknowledged(messageId)
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
            canonicalPeer to entities.map { it.toChatMessage() }
        }
