package com.example.testresqmesh.core.network.bluetooth.admission

import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogEntry
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionDecision
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionReason
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.macRegex

internal class BleAdmissionDiagnostics(private val capabilities: BleAdmissionCapabilities) {
    private val store: BleStateStore get() = capabilities.store
    private val localName: () -> String get() = capabilities.localName
    private val directLinkCount: () -> Int get() = capabilities.directLinkCount
    private val maxDirectLinks: () -> Int get() = capabilities.maxDirectLinks
    private val electionScore: () -> String get() = capabilities.electionScore
    private val handshakeInfo: () -> Pair<String?, Long> get() = capabilities.handshakeInfo
    private val distinctReadyPeerCount: () -> Int get() = capabilities.distinctReadyPeerCount
    private val onDecision: ((BleAdmissionDecision) -> Unit)? get() = capabilities.onDecision

    fun recordDecision(
        peerName: String,
        endpoint: String,
        peerConnections: Int,
        peerScore: String,
        hasIndirectRoute: Boolean,
        reason: BleAdmissionReason,
        result: BleConnectStartResult,
        attempts: Int,
        candidateAgeMs: Long,
        now: Long
    ) {
        val (rawHsOwner, hsAge) = handshakeInfo()
        val hsOwner = sanitizeOwner(rawHsOwner)
        val lockOwner = store.connectingMacAddress?.let { TerminalLogEntry.shortEndpoint(it) } ?: "none"
        val lockAge = if (store.connectLockAcquiredAt > 0) (now - store.connectLockAcquiredAt).coerceAtLeast(0L) else 0L

        val localScore = electionScore()
        val electionWinner = when {
            peerScore.isNotEmpty() && localScore.isNotEmpty() -> when {
                localScore > peerScore -> "local"
                localScore < peerScore -> "peer"
                else -> when {
                    NodeIdentity.idOf(localName()).orEmpty() > NodeIdentity.idOf(peerName).orEmpty() -> "local"
                    NodeIdentity.idOf(localName()).orEmpty() < NodeIdentity.idOf(peerName).orEmpty() -> "peer"
                    else -> "tie"
                }
            }
            peerScore.isEmpty() && localName().isNotEmpty() -> when {
                localName() > peerName -> "local"
                localName() < peerName -> "peer"
                else -> "tie"
            }
            else -> "unknown"
        }

        val directSockets = store.activeConnections.size + store.activeServerConnections.size
        val distinctDirectPeers = directLinkCount()
        val distinctReadyPeers = distinctReadyPeerCount()

        val decision = BleAdmissionDecision(
            peerSafeLabel = safeNodeLabel(peerName, endpoint),
            endpointSuffix = TerminalLogEntry.shortEndpoint(endpoint),
            reason = reason,
            result = result,
            distinctDirectPeers = distinctDirectPeers,
            distinctReadyPeers = distinctReadyPeers,
            directSockets = directSockets,
            maxDirectLinks = maxDirectLinks(),
            peerAdvertisedConnections = peerConnections,
            hasIndirectRoute = hasIndirectRoute,
            localScore = localScore,
            peerScore = peerScore,
            electionWinner = electionWinner,
            candidateAgeMs = candidateAgeMs,
            attempts = attempts,
            handshakeOwner = hsOwner,
            handshakeAgeMs = hsAge,
            lockOwner = lockOwner,
            lockAgeMs = lockAge
        )

        AppLogger.d("BLE_ADMISSION", decision.toLogString())
        onDecision?.invoke(decision)
    }

    private fun safeNodeLabel(peerName: String, endpoint: String): String {
        val id = NodeIdentity.idOf(peerName)
        if (id != null) return "node:$id"
        val advertisedNodeId = store.endpointNodeIds[endpoint]
        if (!advertisedNodeId.isNullOrEmpty()) return "node:$advertisedNodeId"
        val key = NodeIdentity.key(peerName)
        val hash = if (key.isNotEmpty()) key.hashCode().toUInt().toString(16) else endpoint.takeLast(4)
        return "id:$hash"
    }

    private fun sanitizeOwner(owner: String?): String {
        if (owner.isNullOrBlank()) return "none"
        return macRegex.replace(owner) { match -> TerminalLogEntry.shortEndpoint(match.value) }
    }
}
