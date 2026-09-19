package com.example.testresqmesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.testresqmesh.data.local.entity.AttachmentEntity
import com.example.testresqmesh.data.local.entity.AttachmentTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE attachmentId = :attachmentId")
    suspend fun find(attachmentId: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransfer(transfer: AttachmentTransferEntity): Long

    @Query("SELECT * FROM attachment_transfers WHERE attachmentId = :attachmentId AND peerNodeId = :peerNodeId")
    suspend fun findTransfer(attachmentId: String, peerNodeId: String): AttachmentTransferEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE isOutgoing = 1 AND status = 'TRANSFERRING' AND attachmentId != :attachmentId")
    suspend fun activeOutgoingCountExcept(attachmentId: String): Int
}
