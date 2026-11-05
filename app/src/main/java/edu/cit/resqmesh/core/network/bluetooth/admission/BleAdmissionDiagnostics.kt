package edu.cit.resqmesh.core.network.bluetooth.admission

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogEntry
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionDecision
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionReason
import edu.cit.resqmesh.core.network.bluetooth.BleConnectStartResult
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.macRegex

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
        val electionWinner = electionWinner(localScore, peerScore, peerName)

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

    /**
     * Which side should initiate: the higher election score, then the higher node ID; peers that
     * advertise no score fall back to comparing names. Diagnostic only.
     */
    private fun electionWinner(localScore: String, peerScore: String, peerName: String): String = when {
        peerScore.isNotEmpty() && localScore.isNotEmpty() -> when {
            localScore > peerScore -> "local"
            localScore < peerScore -> "peer"
            else -> compareSides(NodeIdentity.idOf(localName()).orEmpty(), NodeIdentity.idOf(peerName).orEmpty())
        }
        peerScore.isEmpty() && localName().isNotEmpty() -> compareSides(localName(), peerName)
        else -> "unknown"
    }

    private fun compareSides(local: String, peer: String): String = when {
        local > peer -> "local"
        local < peer -> "peer"
        else -> "tie"
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
