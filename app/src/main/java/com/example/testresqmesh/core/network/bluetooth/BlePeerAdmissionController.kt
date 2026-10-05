package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionCapabilities
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionSession
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionDiagnostics
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdvertisementAdmission
import com.example.testresqmesh.core.network.bluetooth.admission.BleBootstrapQueue
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants
import android.os.Handler
import androidx.annotation.VisibleForTesting
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore


/** Applies discovery identity, capacity, orphan-rescue, and initiator-election policy. */
class BlePeerAdmissionController(
    private val store: BleStateStore,
    private val scheduler: BleAdmissionScheduler,
    private val localName: () -> String,
    private val isBlocked: (String) -> Boolean,
    private val hasLinkToIdentity: (String) -> Boolean,
    private val hasIndirectRoute: (String) -> Boolean,
    private val hasPayloadReadyDirectLink: () -> Boolean,
    private val hasReadyLinkToIdentity: (String) -> Boolean,
    private val directLinkCount: () -> Int,
    private val maxDirectLinks: () -> Int,
    private val electionScore: () -> String,
    private val latestEndpointForIdentity: (String, String) -> String,
    private val connect: (String, String) -> BleConnectStartResult,
    private val onScanned: (ScanEvent) -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onPulse: () -> Unit,
    private val handshakeInfo: () -> Pair<String?, Long> = { null to 0L },
    private val distinctReadyPeerCount: () -> Int = {
        (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { store.links.isReady(it) }
            .map { endpoint ->
                store.endpointNodeIds[endpoint]
                    ?: NodeIdentity.key(store.connectedEndpointNames[endpoint]).ifEmpty { endpoint }
            }
            .toSet()
            .size
    },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val jitterMs: (Long, Long) -> Long = { min, max -> (min..max).random() },
    private val onDecision: ((BleAdmissionDecision) -> Unit)? = null,
    private val disconnectEndpoint: (String) -> Unit = {},
    private val isNodeActive: () -> Boolean = { true },
    private val canRetireForBridge: (String) -> Boolean = { false },
    private val isTransportIdle: (String) -> Boolean = { true }
) {
    constructor(
        store: BleStateStore,
        handler: Handler,
        localName: () -> String,
        isBlocked: (String) -> Boolean,
        hasLinkToIdentity: (String) -> Boolean,
        hasIndirectRoute: (String) -> Boolean,
        hasPayloadReadyDirectLink: () -> Boolean,
        hasReadyLinkToIdentity: (String) -> Boolean,
        directLinkCount: () -> Int,
        maxDirectLinks: () -> Int,
        electionScore: () -> String,
        latestEndpointForIdentity: (String, String) -> String,
        connect: (String, String) -> BleConnectStartResult,
        onScanned: (ScanEvent) -> Unit,
        onDisconnected: (String) -> Unit,
        onPulse: () -> Unit,
        handshakeInfo: () -> Pair<String?, Long> = { null to 0L },
        distinctReadyPeerCount: () -> Int = {
            (store.activeConnections.keys + store.activeServerConnections.keys)
                .filter { store.links.isReady(it) }
                .map { endpoint ->
                    store.endpointNodeIds[endpoint]
                        ?: NodeIdentity.key(store.connectedEndpointNames[endpoint]).ifEmpty { endpoint }
                }
                .toSet()
                .size
        },
        disconnectEndpoint: (String) -> Unit = {},
        isNodeActive: () -> Boolean = { true },
        canRetireForBridge: (String) -> Boolean = { false },
        isTransportIdle: (String) -> Boolean = { true }
    ) : this(
        store = store,
        scheduler = HandlerAdmissionScheduler(handler),
        localName = localName,
        isBlocked = isBlocked,
        hasLinkToIdentity = hasLinkToIdentity,
        hasIndirectRoute = hasIndirectRoute,
        hasPayloadReadyDirectLink = hasPayloadReadyDirectLink,
        hasReadyLinkToIdentity = hasReadyLinkToIdentity,
        directLinkCount = directLinkCount,
        maxDirectLinks = maxDirectLinks,
        electionScore = electionScore,
        latestEndpointForIdentity = latestEndpointForIdentity,
        connect = connect,
        onScanned = onScanned,
        onDisconnected = onDisconnected,
        onPulse = onPulse,
        handshakeInfo = handshakeInfo,
        distinctReadyPeerCount = distinctReadyPeerCount,
        disconnectEndpoint = disconnectEndpoint,
        isNodeActive = isNodeActive,
        canRetireForBridge = canRetireForBridge,
        isTransportIdle = isTransportIdle
    )

    private val capabilities = BleAdmissionCapabilities(
        store,
        scheduler,
        localName,
        isBlocked,
        hasLinkToIdentity,
        hasIndirectRoute,
        hasPayloadReadyDirectLink,
        hasReadyLinkToIdentity,
        directLinkCount,
        maxDirectLinks,
        electionScore,
        latestEndpointForIdentity,
        connect,
        onScanned,
        onDisconnected,
        onPulse,
        handshakeInfo,
        distinctReadyPeerCount,
        clock,
        jitterMs,
        onDecision,
        disconnectEndpoint,
        isNodeActive,
        canRetireForBridge,
        isTransportIdle
    )
    private val session = BleAdmissionSession(scheduler, isNodeActive)
    private val diagnostics = BleAdmissionDiagnostics(capabilities)
    private val advertisements: BleAdvertisementAdmission = BleAdvertisementAdmission(capabilities, session, diagnostics, { bootstrap })
    private val bootstrap: BleBootstrapQueue = BleBootstrapQueue(capabilities, session, diagnostics, advertisements::admitOrElect)

    fun stop() = session.stop()
    fun recover() = advertisements.recover()
    fun handle(advertisement: BleAdvertisement) = advertisements.handle(advertisement, observed = true)
    @VisibleForTesting
    internal fun drainCandidates() = bootstrap.drainCandidates()

    companion object {
        const val CANDIDATE_FRESH_MS = BleAdmissionConstants.CANDIDATE_FRESH_MS
        const val MAX_CANDIDATES = BleAdmissionConstants.MAX_CANDIDATES
        const val BRIDGE_COOLDOWN_MS = BleAdmissionConstants.BRIDGE_COOLDOWN_MS
        const val ORPHAN_RESCUE_DELAY_MS = BleAdmissionConstants.ORPHAN_RESCUE_DELAY_MS
        const val MIN_CONNECTION_JITTER_MS = BleAdmissionConstants.MIN_CONNECTION_JITTER_MS
        const val MAX_CONNECTION_JITTER_MS = BleAdmissionConstants.MAX_CONNECTION_JITTER_MS
        const val FALLBACK_INITIATOR_DELAY_MS = BleAdmissionConstants.FALLBACK_INITIATOR_DELAY_MS
        val macRegex = BleAdmissionConstants.macRegex
    }
}
