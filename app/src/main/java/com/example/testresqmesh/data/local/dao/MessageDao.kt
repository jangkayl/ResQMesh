package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.testresqmesh.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
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
            com.example.testresqmesh.core.model.NodeIdentity.matches(existing.senderName, message.senderName)) 0 else -1
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
