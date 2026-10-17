package edu.cit.resqmesh.core.network.bluetooth.admission

import edu.cit.resqmesh.core.model.ScanEvent
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionScheduler
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionDecision
import edu.cit.resqmesh.core.network.bluetooth.BleConnectStartResult

internal class BleAdmissionCapabilities(
    val store: BleStateStore,
    val scheduler: BleAdmissionScheduler,
    val localName: () -> String,
    val isBlocked: (String) -> Boolean,
    val hasLinkToIdentity: (String) -> Boolean,
    val hasIndirectRoute: (String) -> Boolean,
    val hasPayloadReadyDirectLink: () -> Boolean,
    val hasReadyLinkToIdentity: (String) -> Boolean,
    val directLinkCount: () -> Int,
    val maxDirectLinks: () -> Int,
    val electionScore: () -> String,
    val latestEndpointForIdentity: (String, String) -> String,
    val connect: (String, String) -> BleConnectStartResult,
    val onScanned: (ScanEvent) -> Unit,
    val onDisconnected: (String) -> Unit,
    val onPulse: () -> Unit,
    val handshakeInfo: () -> Pair<String?, Long>,
    val distinctReadyPeerCount: () -> Int,
    val clock: () -> Long,
    val jitterMs: (Long, Long) -> Long,
    val onDecision: ((BleAdmissionDecision) -> Unit)?,
    val disconnectEndpoint: (String) -> Unit,
    val isNodeActive: () -> Boolean,
    val canRetireForBridge: (String) -> Boolean,
    val isTransportIdle: (String) -> Boolean
)
