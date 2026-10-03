package com.example.testresqmesh.core.network

/** Local transport progress only; COMPLETED is not an end-to-end delivery receipt. */
data class OutboundFrameEvent(
    val transmissionId: String,
    val messageId: String,
    val stage: Stage,
    val transport: String,
    val generation: Long,
    val elapsedMs: Long,
    val bytes: Int,
    val queueMs: Long = 0,
    val transferMs: Long = 0,
    val isPrivate: Boolean = false,
    val hops: Int = 1
) {
    enum class Stage { QUEUED, STARTED, COMPLETED, FAILED }
}
