package com.example.testresqmesh.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MeshPayload(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val type: String = "MESSAGE", // MESSAGE, SYSTEM, SEEN, DELIVERED, LIVE_AUDIO
    @ProtoNumber(3) val senderName: String = "",
    @ProtoNumber(4) val targetName: String = "",
    @ProtoNumber(5) val text: String = "",
    @ProtoNumber(6) val imageBytes: ByteArray? = null,
    @ProtoNumber(7) val audioBytes: ByteArray? = null,
    @ProtoNumber(8) val isPrivate: Boolean = false,
    @ProtoNumber(9) val isEncrypted: Boolean = false,
    @ProtoNumber(10) val encryptedData: String? = null,
    @ProtoNumber(11) val encryptedKey: String? = null,
    @ProtoNumber(12) val locationLat: Double? = null,
    @ProtoNumber(13) val locationLng: Double? = null,
    @ProtoNumber(14) val routePath: List<String> = emptyList(),
    @ProtoNumber(15) val directedRoute: List<String> = emptyList(),
    @ProtoNumber(16) val returnRoute: List<String> = emptyList(),
    @ProtoNumber(17) val isSOS: Boolean = false,
    @ProtoNumber(18) val channelId: String = "1",
    @ProtoNumber(19) val isSOSCancel: Boolean = false,
    @ProtoNumber(20) val publicKey: String = "",
    @ProtoNumber(21) val connectedNodes: List<String> = emptyList(),
    @ProtoNumber(22) val targetMessageId: String = "",
    @ProtoNumber(23) val reader: String = "",
    @ProtoNumber(24) val liveAudioChunk: ByteArray? = null,
    /** Stable identity fields are optional so older peers can still decode the envelope. */
    @ProtoNumber(25) val senderNodeId: String = "",
    @ProtoNumber(26) val targetNodeId: String = "",
    @ProtoNumber(27) val connectedNodeIds: List<String> = emptyList(),
    @ProtoNumber(28) val directedRouteNodeIds: List<String> = emptyList(),
    @ProtoNumber(29) val returnRouteNodeIds: List<String> = emptyList(),
    @ProtoNumber(30) val relayHopCount: Int = 0,
    /** Attachment fields are metadata/chunks only; an image is never embedded in this envelope. */
    @ProtoNumber(31) val attachmentId: String = "",
    @ProtoNumber(32) val attachmentSequence: Int = 0,
    @ProtoNumber(33) val attachmentTotalChunks: Int = 0,
    @ProtoNumber(34) val attachmentCiphertext: ByteArray? = null,
    @ProtoNumber(35) val attachmentNonce: ByteArray? = null,
    /** Realtime voice metadata. These fields bound frames; audio itself is never persisted here. */
    @ProtoNumber(36) val liveVoiceSessionId: String = "",
    @ProtoNumber(37) val liveVoiceSequence: Int = 0,
    @ProtoNumber(38) val liveVoiceCapturedAtMs: Long = 0L,
    @ProtoNumber(39) val liveVoiceTtlMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MeshPayload
        if (id != other.id) return false
        return true
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
}
