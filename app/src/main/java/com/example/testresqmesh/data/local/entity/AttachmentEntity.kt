package com.example.testresqmesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Private attachment bytes live at [localPath]; Room only holds resumable transfer metadata. */
@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val attachmentId: String,
    val messageId: String,
    val peerName: String,
    val directedRouteNodeIds: String,
    val isOutgoing: Boolean,
    val status: String,
    val byteSize: Int,
    val width: Int,
    val height: Int,
    val sha256: String,
    val previewPath: String?,
    val localPath: String?,
    val tempPath: String?,
    /** Base64 key material is private app data and is never logged or placed in a message. */
    val keyMaterialBase64: String?,
    val receivedBytes: Int = 0,
    val totalChunks: Int = 0,
    val sourceExpiresAt: Long = 0L,
    val failureReason: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
