package com.example.testresqmesh.core.model

/** Metadata only: image bytes are kept in app-private files, never in a chat row. */
data class AttachmentUiState(
    val attachmentId: String,
    val messageId: String,
    val status: AttachmentStatus,
    val previewPath: String? = null,
    val localPath: String? = null,
    val byteSize: Int = 0,
    val receivedBytes: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val failureReason: String? = null
)

enum class AttachmentStatus {
    QUEUED_OFFER,
    OFFERED,
    DOWNLOAD_REQUESTED,
    TRANSFERRING,
    PAUSED_ROUTE_UNAVAILABLE,
    COMPLETE,
    CORRUPT_RETRY,
    CANCELLED,
    SOURCE_UNAVAILABLE
}
