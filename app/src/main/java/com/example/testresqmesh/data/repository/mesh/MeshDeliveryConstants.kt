package com.example.testresqmesh.data.repository.mesh



internal object MeshDeliveryConstants {
    const val BLOCK_REQUEST_TYPE = "BLOCK_REQUEST"
    const val BLOCK_ACK_TYPE = "BLOCK_ACK"
    const val BLOCK_DIRECT_GRACE_MS = 2_000L
    const val BLOCK_ACK_GRACE_MS = 1_000L
    const val BLOCK_RETRY_MS = 3_000L
    const val PRIVATE_DELIVERY_TIMEOUT_MS = 15_000L
    const val OUTBOX_RETRY_BACKOFF_MS = 5_000L
    const val OUTBOX_EXPIRY_MS = 24 * 60 * 60 * 1000L
}
