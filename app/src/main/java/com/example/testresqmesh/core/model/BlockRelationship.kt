package com.example.testresqmesh.core.model

enum class BlockRelationshipOrigin {
    LOCAL,
    REMOTE
}

enum class BlockRelationshipStatus {
    PENDING_ACK,
    CONFIRMED,
    RELEASED_LOCALLY
}

/**
 * A durable relationship keyed by [NodeIdentity.key]. It controls only whether a direct BLE link
 * may exist; it never suppresses relayed application payloads from this peer.
 */
data class BlockRelationship(
    val peerName: String,
    val operationId: String,
    val origin: BlockRelationshipOrigin,
    val status: BlockRelationshipStatus,
    val updatedAt: Long,
    /** Encrypted outer request bytes encoded for retry; never plaintext control content. */
    val sealedRequest: String = "",
    /** Initiator public key retained by the remote side to encrypt an acknowledgement after retry. */
    val replyPublicKey: String = ""
) {
    val deniesDirectLink: Boolean
        get() = status != BlockRelationshipStatus.RELEASED_LOCALLY
}
