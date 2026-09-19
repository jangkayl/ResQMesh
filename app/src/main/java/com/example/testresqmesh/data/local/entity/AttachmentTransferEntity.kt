package com.example.testresqmesh.data.local.entity

import androidx.room.Entity

/** One private image has one recipient in v1, but checkpoints remain independently durable. */
@Entity(tableName = "attachment_transfers", primaryKeys = ["attachmentId", "peerNodeId"])
data class AttachmentTransferEntity(
    val attachmentId: String,
    val peerNodeId: String,
    val nextChunk: Int,
    val state: String,
    val updatedAt: Long = System.currentTimeMillis()
)
