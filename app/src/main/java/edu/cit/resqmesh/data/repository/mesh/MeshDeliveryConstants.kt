package edu.cit.resqmesh.data.repository.mesh



internal object MeshDeliveryConstants {
    const val BLOCK_REQUEST_TYPE = "BLOCK_REQUEST"
    const val BLOCK_ACK_TYPE = "BLOCK_ACK"
    const val BLOCK_DIRECT_GRACE_MS = 2_000L
    const val BLOCK_ACK_GRACE_MS = 1_000L
    const val BLOCK_RETRY_MS = 3_000L
    const val BLOCK_RETRY_MAX_MS = 60_000L
    /** Covers the direct grace plus the ACK grace, so the gate does not cut a live block handshake. */
    const val BLOCK_TEARDOWN_GRACE_MS = 3_000L
    /** Minimum gap between direct request deliveries to one peer on fresh links. */
    const val BLOCK_DELIVERY_MIN_INTERVAL_MS = 30_000L
    const val PRIVATE_DELIVERY_TIMEOUT_MS = 15_000L
    const val PRIVATE_RECEIPT_POLL_MS = 1_000L
    const val PRIVATE_NO_PROGRESS_TIMEOUT_MS = 60_000L
    const val MAX_PRIVATE_ACCEPTED_ATTEMPTS = 3
    const val OUTBOX_RETRY_BACKOFF_MS = 5_000L
    const val MIN_BUDGET_RETRY_MS = 250L
    const val OUTBOX_EXPIRY_MS = 24 * 60 * 60 * 1000L
}
