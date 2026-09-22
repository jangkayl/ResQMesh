package com.example.testresqmesh.core.network

/** Immediate ownership result; delivery is confirmed separately by an end-to-end receipt. */
enum class TransportDispatchResult {
    ACCEPTED,
    REJECTED_INVALID_ENDPOINT,
    REJECTED_NOT_READY,
    REJECTED_QUEUE_FULL,
    REJECTED_INVALID_FRAME;

    val accepted: Boolean get() = this == ACCEPTED
}
