package com.example.testresqmesh.core.network.bluetooth.admission

import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionScheduler
import com.example.testresqmesh.core.network.bluetooth.BleAdvertisement
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionDecision
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionReason
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult

internal interface BleAdmissionContext {
    val capabilities: BleAdmissionCapabilities
    val session: BleAdmissionSession
    val diagnostics: BleAdmissionDiagnostics
    val store: BleStateStore get() = capabilities.store
    val scheduler: BleAdmissionScheduler get() = capabilities.scheduler
    val localName: () -> String get() = capabilities.localName
    val isBlocked: (String) -> Boolean get() = capabilities.isBlocked
    val hasLinkToIdentity: (String) -> Boolean get() = capabilities.hasLinkToIdentity
    val hasIndirectRoute: (String) -> Boolean get() = capabilities.hasIndirectRoute
    val hasPayloadReadyDirectLink: () -> Boolean get() = capabilities.hasPayloadReadyDirectLink
    val hasReadyLinkToIdentity: (String) -> Boolean get() = capabilities.hasReadyLinkToIdentity
    val directLinkCount: () -> Int get() = capabilities.directLinkCount
    val maxDirectLinks: () -> Int get() = capabilities.maxDirectLinks
    val electionScore: () -> String get() = capabilities.electionScore
    val latestEndpointForIdentity: (String, String) -> String get() = capabilities.latestEndpointForIdentity
    val connect: (String, String) -> BleConnectStartResult get() = capabilities.connect
    val onScanned: (ScanEvent) -> Unit get() = capabilities.onScanned
    val onDisconnected: (String) -> Unit get() = capabilities.onDisconnected
    val onPulse: () -> Unit get() = capabilities.onPulse
    val handshakeInfo: () -> Pair<String?, Long> get() = capabilities.handshakeInfo
    val distinctReadyPeerCount: () -> Int get() = capabilities.distinctReadyPeerCount
    val clock: () -> Long get() = capabilities.clock
    val jitterMs: (Long, Long) -> Long get() = capabilities.jitterMs
    val onDecision: ((BleAdmissionDecision) -> Unit)? get() = capabilities.onDecision
    val disconnectEndpoint: (String) -> Unit get() = capabilities.disconnectEndpoint
    val isNodeActive: () -> Boolean get() = capabilities.isNodeActive
    val canRetireForBridge: (String) -> Boolean get() = capabilities.canRetireForBridge
    val isTransportIdle: (String) -> Boolean get() = capabilities.isTransportIdle
    val candidates: MutableMap<String, BootstrapCandidate> get() = session.candidates
    val advertisements: MutableMap<String, Pair<BleAdvertisement, Long>> get() = session.advertisements
    var generation: Long
        get() = session.generation
        set(value) { session.generation = value }
    var drainScheduled: Boolean
        get() = session.drainScheduled
        set(value) { session.drainScheduled = value }
    var lastBridgeAt: Long
        get() = session.lastBridgeAt
        set(value) { session.lastBridgeAt = value }
    fun later(delay: Long = 0L, action: () -> Unit) = session.later(delay, action)
    fun candidateIdentity(peerName: String, endpoint: String) = session.candidateIdentity(peerName, endpoint)
    fun recordDecision(peerName: String, endpoint: String, peerConnections: Int, peerScore: String,
        hasIndirectRoute: Boolean, reason: BleAdmissionReason, result: BleConnectStartResult,
        attempts: Int, candidateAgeMs: Long, now: Long) = diagnostics.recordDecision(peerName,
            endpoint, peerConnections, peerScore, hasIndirectRoute, reason, result, attempts, candidateAgeMs, now)
}
