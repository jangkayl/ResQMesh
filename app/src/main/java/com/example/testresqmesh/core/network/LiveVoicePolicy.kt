package com.example.testresqmesh.core.network

/**
 * Bounds a walkie-talkie frame to the moment it is useful. Live speech is never a
 * delay-tolerant payload: a reconnect may carry new frames, but must not replay old ones.
 */
object LiveVoicePolicy {
    const val FRAME_TTL_MS = 2_500L
    const val MAX_RELAY_HOPS = 4

    enum class FrameDecision { ACCEPT, EXPIRED, HOP_LIMIT, MALFORMED, OLD_SEQUENCE }

    fun validate(
        sessionId: String,
        sequence: Int,
        capturedAtMs: Long,
        relayHopCount: Int,
        lastAcceptedSequence: Int?,
        nowMs: Long
    ): FrameDecision = when {
        sessionId.isBlank() || sequence < 0 || capturedAtMs <= 0 -> FrameDecision.MALFORMED
        relayHopCount !in 0..MAX_RELAY_HOPS -> FrameDecision.HOP_LIMIT
        nowMs - capturedAtMs > FRAME_TTL_MS -> FrameDecision.EXPIRED
        lastAcceptedSequence != null && sequence <= lastAcceptedSequence -> FrameDecision.OLD_SEQUENCE
        else -> FrameDecision.ACCEPT
    }
}
