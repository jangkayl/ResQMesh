package com.example.testresqmesh.core.network

/** Pure bounds and scheduling policy shared by the attachment protocol and its unit tests. */
object AttachmentTransferPolicy {
    const val CHUNK_BYTES = 1024
    const val MAX_IMAGE_BYTES = 320 * 1024
    const val MAX_PREVIEW_BYTES = 8 * 1024
    const val MAX_CHUNKS = MAX_IMAGE_BYTES / CHUNK_BYTES

    enum class SequenceResult { ACCEPT, REPEAT_CHECKPOINT, REJECT }
    enum class Traffic { LOW_ATTACHMENT, PRIORITY_CONTROL }

    fun chunkCount(byteSize: Int): Int = if (byteSize !in 1..MAX_IMAGE_BYTES) 0 else (byteSize + CHUNK_BYTES - 1) / CHUNK_BYTES

    fun sequenceResult(expected: Int, received: Int, totalChunks: Int): SequenceResult = when {
        totalChunks !in 1..MAX_CHUNKS || expected !in 0 until totalChunks || received !in 0 until totalChunks -> SequenceResult.REJECT
        received == expected -> SequenceResult.ACCEPT
        else -> SequenceResult.REPEAT_CHECKPOINT
    }

    fun trafficFor(payloadType: String): Traffic = if (payloadType == "ATTACHMENT_CHUNK") Traffic.LOW_ATTACHMENT else Traffic.PRIORITY_CONTROL
}
