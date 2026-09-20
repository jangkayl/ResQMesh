package com.example.testresqmesh.core.network.bluetooth

/**
 * Categorical reason for an admission or initiator-election decision.
 */
enum class BleAdmissionReason {
    BLOCKED,
    SELF_MATCH,
    ALREADY_CONNECTED,
    ROUTE_PRESERVED,
    CAPACITY_LIMIT,
    TWO_LINK_PEER_CONNECTED,
    ELECTION_YIELD,
    QUEUED_INITIATOR,
    ACTIVE_SETUP,
    ORPHAN_EVALUATING,
    DRAIN_STARTED,
    DRAIN_DEFERRED,
    DRAIN_REJECTED,
    STALE_REMOVED
}

/**
 * Structured, privacy-safe snapshot of a BLE admission decision.
 *
 * Excludes message payloads, encryption keys, full MAC addresses, and location coordinates.
 */
data class BleAdmissionDecision(
    val peerSafeLabel: String,
    val endpointSuffix: String,
    val reason: BleAdmissionReason,
    val result: BleConnectStartResult,
    val distinctDirectPeers: Int,
    val distinctReadyPeers: Int,
    val directSockets: Int,
    val maxDirectLinks: Int,
    val peerAdvertisedConnections: Int,
    val hasIndirectRoute: Boolean,
    val localScore: String,
    val peerScore: String,
    val electionWinner: String,
    val candidateAgeMs: Long,
    val attempts: Int,
    val handshakeOwner: String,
    val handshakeAgeMs: Long,
    val lockOwner: String,
    val lockAgeMs: Long
) {
    fun toLogString(): String =
        "peer=$peerSafeLabel endpoint=$endpointSuffix reason=$reason result=$result " +
        "peers=$distinctDirectPeers readyPeers=$distinctReadyPeers sockets=$directSockets max=$maxDirectLinks peerConns=$peerAdvertisedConnections " +
        "route=$hasIndirectRoute election=[local=$localScore peer=$peerScore winner=$electionWinner] " +
        "candidate=[age=${candidateAgeMs}ms attempts=$attempts] " +
        "handshake=[owner=$handshakeOwner age=${handshakeAgeMs}ms] " +
        "lock=[owner=$lockOwner age=${lockAgeMs}ms]"
}
