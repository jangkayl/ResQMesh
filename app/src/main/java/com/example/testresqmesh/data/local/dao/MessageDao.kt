package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE targetName IS NULL ORDER BY timestamp ASC")
    fun getPublicMessages(): Flow<List<MessageEntity>>

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

    @Query("UPDATE messages SET deliveredTo = :deliveredTo WHERE msgId = :msgId")
    suspend fun updateDeliveredTo(msgId: String, deliveredTo: String): Int

    @Query("UPDATE messages SET seenBy = :seenBy WHERE msgId = :msgId")
    suspend fun updateSeenBy(msgId: String, seenBy: String): Int

    @Query("SELECT * FROM messages")
    suspend fun getAllMessagesOnce(): List<MessageEntity>
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages(): Int

    @Query("DELETE FROM messages WHERE targetName = :peerName OR (senderName = :peerName AND targetName IS NOT NULL)")
    suspend fun deleteConversationWith(peerName: String): Int
}
