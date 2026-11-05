package edu.cit.resqmesh.data.repository

import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.data.local.dao.MessageDao
import edu.cit.resqmesh.data.local.entity.ChatSyncEntry
import edu.cit.resqmesh.data.local.entity.ChatSyncRow
import edu.cit.resqmesh.data.local.entity.MessageEntity
import edu.cit.resqmesh.data.local.entity.StoredNameList
import edu.cit.resqmesh.data.local.entity.toMessageEntity
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
    /** The stored recipient of one of our private messages, or null. */
    suspend fun privateTargetOf(messageId: String): String? = null
    /** Removes one of our own unreceipted private messages; true if a row was deleted. */
    suspend fun deleteOwnUnreceiptedPrivate(messageId: String): Boolean = false
    suspend fun isOutstandingPrivateSend(messageId: String): Boolean = isUnacknowledgedPrivateSend(messageId) ||
        getPendingOutbox().any { (message, target) -> message.id == messageId && target != null }
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
    /** Newest offerable Community and SOS messages, [limit] per conversation. */
    suspend fun chatSyncIndex(limit: Int, maxSosThreads: Int): List<ChatSyncEntry> = emptyList()
    suspend fun chatSyncRows(ids: List<String>): List<ChatSyncRow> = emptyList()
    suspend fun chatSyncMessage(id: String): ChatMessage? = null
    /** Placeholders still worth fetching, newest first. Those past the newest [limit] per conversation give up. */
    suspend fun pendingSyncMedia(limit: Int): List<String> = emptyList()
    /** True when [id] was a placeholder from [senderNodeId] and now holds its attachment. */
    suspend fun fillSyncedMedia(id: String, senderNodeId: String, image: String?, audio: String?): Boolean = false
}

enum class PrivateMessageInsert { INSERTED, DUPLICATE, REJECTED }

class RoomMessageStore(private val dao: MessageDao) : MessageStore {
    override suspend fun isOutstandingPrivateSend(messageId: String): Boolean = dao.getMessageById(messageId)?.let {
        it.isMine && it.targetName != null && it.deliveredTo in setOf("", "PENDING") && it.seenBy.isEmpty()
    } == true
    override suspend fun incomingPrivateWasSeen(messageId: String): Boolean =
        dao.getMessageById(messageId)?.let { !it.isMine && it.targetName != null && it.seenBy.isNotEmpty() } == true
    override suspend fun recoverUnacknowledgedPrivateSends() { dao.recoverUnacknowledgedPrivateSends() }
    override suspend fun isUnacknowledgedPrivateSend(messageId: String): Boolean =
        dao.getMessageById(messageId)?.let { it.isMine && it.targetName != null && it.deliveredTo.isEmpty() && it.seenBy.isEmpty() } == true
    override suspend fun privateTargetOf(messageId: String): String? =
        dao.getMessageById(messageId)?.takeIf { it.isMine }?.targetName
    override suspend fun deleteOwnUnreceiptedPrivate(messageId: String): Boolean {
        val row = dao.getMessageById(messageId)
        val unreceipted = row != null && row.seenBy.isEmpty() && row.deliveredTo in setOf("", "PENDING", "FAILED")
        val ours = unreceipted && row?.isMine == true && row.targetName != null
        return ours && dao.deleteMessagesByIds(listOf(messageId)) > 0
    }
    override suspend fun saveIncomingPrivateIfAbsent(message: ChatMessage): PrivateMessageInsert =
        when (dao.insertIncomingPrivateIfAbsent(message.toMessageEntity(message.senderName))) {
            1 -> PrivateMessageInsert.INSERTED
            0 -> PrivateMessageInsert.DUPLICATE
            else -> PrivateMessageInsert.REJECTED
        }
    override suspend fun contains(id: String) = dao.getMessageById(id) != null
    override suspend fun chatSyncIndex(limit: Int, maxSosThreads: Int): List<ChatSyncEntry> =
        dao.chatSyncCommunity(limit) +
            dao.chatSyncSosThreads(maxSosThreads).flatMap { dao.chatSyncSosThread(it, limit) }
    override suspend fun chatSyncRows(ids: List<String>): List<ChatSyncRow> = dao.chatSyncRows(ids)
    override suspend fun chatSyncMessage(id: String): ChatMessage? = dao.chatSyncMessage(id)?.toChatMessage()
    override suspend fun pendingSyncMedia(limit: Int): List<String> {
        val pending = dao.pendingSyncMedia()
        val past = pending.groupBy { it.kind to it.sosId }.flatMap { (conversation, rows) ->
            val newest = dao.newestMediaIds(conversation.first, conversation.second, limit).toSet()
            rows.map { it.id }.filterNot { it in newest }
        }.toSet()
        if (past.isNotEmpty()) dao.markMediaNotSynced(past.toList())
        return pending.map { it.id }.filterNot { it in past }
    }
    override suspend fun fillSyncedMedia(id: String, senderNodeId: String, image: String?, audio: String?): Boolean =
        dao.fillSyncedMedia(id, senderNodeId, image, audio) > 0
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
        if (message.isMine && message.targetName != null && !NodeIdentity.matches(message.targetName, readerName)) return
        val readers = StoredNameList.decode(message.deliveredTo)
            .filter { it.isNotEmpty() && it != "FAILED" && it != "PENDING" }
        if (readerName in readers) return
        dao.updateDeliveredTo(messageId, StoredNameList.encode(readers + readerName))
    }

    override suspend fun markSeen(messageId: String, readerName: String) {
        val message = dao.getMessageById(messageId) ?: return
        if (message.isMine && message.targetName != null && !NodeIdentity.matches(message.targetName, readerName)) return
        val readers = StoredNameList.decode(message.seenBy)
            .filter { it.isNotEmpty() && it != "FAILED" && it != "PENDING" }
        if (readerName in readers) return
        dao.updateSeenBy(messageId, StoredNameList.encode(readers + readerName))
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
