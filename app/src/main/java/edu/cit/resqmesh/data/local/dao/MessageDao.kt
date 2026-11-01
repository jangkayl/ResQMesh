package edu.cit.resqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import edu.cit.resqmesh.data.local.entity.ChatSyncEntry
import edu.cit.resqmesh.data.local.entity.ChatSyncRow
import edu.cit.resqmesh.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/** Public rows a neighbor may be offered: never private, never unsent, never without a stable sender. */
private const val CHAT_SYNCABLE = "targetName IS NULL AND isSOS = 0 AND senderNodeId != '' " +
    "AND deliveredTo NOT IN ('PENDING', 'FAILED')"
private const val CHAT_SYNC_ENTRY = "SELECT msgId AS id, timestamp AS sentAt, conversationKind AS kind, senderName, " +
    "sosId, (imageBase64 IS NOT NULL OR audioBase64 IS NOT NULL) AS hasMedia FROM messages"

@Dao
interface MessageDao {
    @Query("$CHAT_SYNC_ENTRY WHERE $CHAT_SYNCABLE AND conversationKind = 'COMMUNITY' " +
        "ORDER BY timestamp DESC, msgId LIMIT :limit")
    suspend fun chatSyncCommunity(limit: Int): List<ChatSyncEntry>

    @Query("$CHAT_SYNC_ENTRY WHERE $CHAT_SYNCABLE AND conversationKind = 'SOS' AND sosId = :sosId " +
        "ORDER BY timestamp DESC, msgId LIMIT :limit")
    suspend fun chatSyncSosThread(sosId: String, limit: Int): List<ChatSyncEntry>

    @Query("SELECT sosId FROM messages WHERE $CHAT_SYNCABLE AND conversationKind = 'SOS' AND sosId != '' " +
        "GROUP BY sosId ORDER BY MAX(timestamp) DESC LIMIT :limit")
    suspend fun chatSyncSosThreads(limit: Int): List<String>

    @Query("SELECT msgId AS id, timestamp AS sentAt, conversationKind AS kind, senderName, senderNodeId, " +
        "COALESCE(text, '') AS text, sosId, locationLat, locationLng FROM messages " +
        "WHERE msgId IN (:ids) AND $CHAT_SYNCABLE AND conversationKind IN ('COMMUNITY', 'SOS') " +
        "AND imageBase64 IS NULL AND audioBase64 IS NULL")
    suspend fun chatSyncRows(ids: List<String>): List<ChatSyncRow>

    @Query("SELECT * FROM messages WHERE targetName IS NULL AND conversationKind = 'COMMUNITY' ORDER BY timestamp ASC")
    fun getPublicMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE targetName IS NULL ORDER BY timestamp ASC")
    fun getAllPublicMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE targetName = :peerName OR (senderName = :peerName AND targetName IS NOT NULL) ORDER BY timestamp ASC")
    fun getPrivateMessagesWith(peerName: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE targetName IS NOT NULL ORDER BY timestamp ASC")
    fun getAllPrivateMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE msgId = :msgId")
    suspend fun getMessageById(msgId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE isSOS = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSOS(): Flow<MessageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageIfAbsent(message: MessageEntity): Long

    @Transaction
    suspend fun insertIncomingPrivateIfAbsent(message: MessageEntity): Int {
        if (insertMessageIfAbsent(message) != -1L) return 1
        val existing = getMessageById(message.msgId) ?: return -1
        return if (!existing.isMine && existing.targetName != null &&
            edu.cit.resqmesh.core.model.NodeIdentity.matches(existing.senderName, message.senderName)) 0 else -1
    }

    @Query("UPDATE messages SET deliveredTo = :deliveredTo WHERE msgId = :msgId")
    suspend fun updateDeliveredTo(msgId: String, deliveredTo: String): Int

    /** A receipt may arrive while the timeout is running. Only an unacknowledged send may fail. */
    @Query("UPDATE messages SET deliveredTo = 'FAILED' WHERE msgId = :msgId AND deliveredTo = '' AND seenBy = ''")
    suspend fun failUnacknowledgedSend(msgId: String): Int

    @Query("UPDATE messages SET deliveredTo = 'FAILED' WHERE msgId = :msgId AND deliveredTo = 'PENDING' AND seenBy = ''")
    suspend fun expirePendingSend(msgId: String): Int

    @Query("UPDATE messages SET deliveredTo = 'PENDING' WHERE isMine = 1 AND targetName IS NOT NULL AND deliveredTo = '' AND seenBy = ''")
    suspend fun recoverUnacknowledgedPrivateSends(): Int

    @Query("UPDATE messages SET deliveredTo = 'PENDING' WHERE msgId = :msgId AND isMine = 1 AND deliveredTo IN ('', 'PENDING', 'FAILED') AND seenBy = ''")
    suspend fun markPendingIfUnacknowledged(msgId: String): Int

    @Query("UPDATE messages SET deliveredTo = '' WHERE msgId = :msgId AND isMine = 1 AND deliveredTo IN ('PENDING', 'FAILED') AND seenBy = ''")
    suspend fun markSentIfUnacknowledged(msgId: String): Int

    @Query("UPDATE messages SET seenBy = :seenBy WHERE msgId = :msgId")
    suspend fun updateSeenBy(msgId: String, seenBy: String): Int

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesOnce(): List<MessageEntity>
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages(): Int

    @Query("SELECT * FROM messages WHERE isMine = 1 AND deliveredTo = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingOutboxMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE targetName = :peerName OR (senderName = :peerName AND targetName IS NOT NULL)")
    suspend fun deleteConversationWith(peerName: String): Int

    @Query("DELETE FROM messages WHERE msgId IN (:messageIds)")
    suspend fun deleteMessagesByIds(messageIds: List<String>): Int
}
