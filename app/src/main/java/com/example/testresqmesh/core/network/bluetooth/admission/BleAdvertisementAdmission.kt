package com.example.testresqmesh.core.network.bluetooth.admission

import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.BleAdvertisement
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionReason
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.CANDIDATE_FRESH_MS
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CANDIDATES
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.FALLBACK_INITIATOR_DELAY_MS

internal class BleAdvertisementAdmission(
    override val capabilities: BleAdmissionCapabilities,
    override val session: BleAdmissionSession,
    override val diagnostics: BleAdmissionDiagnostics,
    private val bootstrap: () -> BleBootstrapQueue
) : BleAdmissionContext {
    private fun drainCandidates() = bootstrap().drainCandidates()
    private fun enqueueCandidate(endpoint: String, peerName: String, peerConnections: Int, now: Long,
        initialDelayMs: Long? = null) = bootstrap().enqueueCandidate(endpoint, peerName, peerConnections, now, initialDelayMs)
    private fun removeCandidate(peerName: String) = bootstrap().removeCandidate(peerName)
    private fun rescueOrphan(endpoint: String, peerName: String, peerConnections: Int, directLinks: Int, now: Long) =
        bootstrap().rescueOrphan(endpoint, peerName, peerConnections, directLinks, now)
    fun recover() {
        if (!isNodeActive()) return
        val now = clock()
        advertisements.entries.removeAll { now - it.value.second > CANDIDATE_FRESH_MS }
        advertisements.values.toList().forEach { (ad, _) -> handle(ad, observed = false) }
        drainCandidates()
    }

    fun handle(advertisement: BleAdvertisement, observed: Boolean) {
        if (!isNodeActive()) return
        val peerName = advertisement.peerName
        val endpoint = advertisement.endpointId
        val now = clock()

        if (isBlocked(peerName) || NodeIdentity.matches(peerName, localName())) {
            val reason = if (isBlocked(peerName)) BleAdmissionReason.BLOCKED else BleAdmissionReason.SELF_MATCH
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = advertisement.directConnections,
                peerScore = advertisement.electionScore,
                hasIndirectRoute = hasIndirectRoute(peerName),
                reason = reason,
                result = BleConnectStartResult.REJECTED,
                attempts = 0,
                candidateAgeMs = 0L,
                now = now
            )
            removeCandidate(peerName)
            return
        }

        if (observed) {
            advertisements[candidateIdentity(peerName, endpoint)] = advertisement to now
            while (advertisements.size > MAX_CANDIDATES) advertisements.remove(advertisements.keys.first())
        }
        evictRotatedGhost(peerName, endpoint)
        if (observed) store.endpointLastSeen[endpoint] = now
        store.endpointLastScore[endpoint] = advertisement.electionScore
        if (advertisement.nodeId.isNotEmpty()) store.endpointNodeIds[endpoint] = advertisement.nodeId
        store.endpointFirstSeen.putIfAbsent(endpoint, now)
        if (store.connectedEndpointIds.add(endpoint)) {
            store.connectedEndpointNames[endpoint] = peerName
            later {
                onScanned(
                    ScanEvent(
                        endpointId = endpoint,
                        name = peerName,
                        nodeId = advertisement.nodeId,
                        peerConnections = advertisement.directConnections,
                        peerScore = advertisement.electionScore,
                        isConnecting = false
                    )
                )
                onPulse()
            }
        }

        // Check for zombie/ghost links from a rebooted peer before checking alreadyConnected.
        // A rebooted peer arrives on a new endpoint MAC and advertises directConnections == 0.
        // If our existing socket to this peer identity is not ready, silent, or peer advertises 0,
        // it is a dead zombie that must be purged to unblock new connection setup.
        val existingEndpoint = store.connectedEndpointNames.entries
            .find { it.key != endpoint && NodeIdentity.matches(it.value, peerName) }?.key
            ?: (advertisement.nodeId.takeIf { it.isNotEmpty() }?.let { id ->
                store.endpointNodeIds.entries.find { it.key != endpoint && it.value == id }?.key
            })

        if (existingEndpoint != null && existingEndpoint != endpoint) {
            val hasLiveRole = store.activeConnections.containsKey(existingEndpoint) || store.activeServerConnections.containsKey(existingEndpoint)
            val establishTime = store.connectionEstablishTime[existingEndpoint] ?: 0L
            val lastInbound = store.connectionInteractionTimes[existingEndpoint] ?: establishTime
            val isOldLinkSilent = (now - lastInbound) > 15_000L
            val isOldLinkAged = (now - establishTime) > 15_000L

            // Only evict as a rebooted zombie if the existing socket is genuinely silent/aged
            // and the peer advertises directConnections == 0. A newly connecting or actively
            // communicating socket must not be torn down simply because the peer's peripheral
            // advertising MAC differs from its central connection MAC.
            val isZombie = hasLiveRole && isOldLinkSilent && isOldLinkAged && advertisement.directConnections == 0

            if (isZombie) {
                AppLogger.d("BLE_MESH", "Purging zombie link $existingEndpoint for rebooted peer $peerName (new endpoint $endpoint)")
                disconnectEndpoint(existingEndpoint)
                store.connectedEndpointIds.remove(existingEndpoint)
                store.connectedEndpointNames.remove(existingEndpoint)
                store.endpointLastSeen.remove(existingEndpoint)
                store.endpointNodeIds.remove(existingEndpoint)
                later { onDisconnected(existingEndpoint) }
            }
        }

        val alreadyConnected = hasLinkToIdentity(peerName) ||
            store.activeConnections.containsKey(endpoint) || store.activeServerConnections.containsKey(endpoint)
        if (alreadyConnected) {
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = advertisement.directConnections,
                peerScore = advertisement.electionScore,
                hasIndirectRoute = hasIndirectRoute(peerName),
                reason = BleAdmissionReason.ALREADY_CONNECTED,
                result = BleConnectStartResult.REJECTED,
                attempts = 0,
                candidateAgeMs = (now - (store.endpointFirstSeen[endpoint] ?: now)).coerceAtLeast(0L),
                now = now
            )
            removeCandidate(peerName)
            return
        }

        // A routed peer normally stays routed to avoid eagerly turning every visible mesh hop into
        // a redundant direct ACL. The exception is bootstrap/recovery: when no payload-ready direct
        // neighbor remains, a nearby unblocked routed peer may restore a direct path. Explicit UI
        // "Connect Directly" uses the same downstream block and duplicate guards.
        if (hasIndirectRoute(peerName) && hasPayloadReadyDirectLink()) {
            AppLogger.d("BLE_MESH", "Deferring direct upgrade to $peerName; healthy payload-ready direct link exists")
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = advertisement.directConnections,
                peerScore = advertisement.electionScore,
                hasIndirectRoute = true,
                reason = BleAdmissionReason.ROUTE_PRESERVED,
                result = BleConnectStartResult.DEFERRED,
                attempts = 0,
                candidateAgeMs = (now - (store.endpointFirstSeen[endpoint] ?: now)).coerceAtLeast(0L),
                now = now
            )
            removeCandidate(peerName)
            return
        }
        if (hasIndirectRoute(peerName)) {
            AppLogger.d("BLE_MESH", "Direct bootstrap candidate $peerName; no payload-ready direct link remains")
        }
        admitOrElect(endpoint, peerName, advertisement, now)
    }

    private fun evictRotatedGhost(peerName: String, endpoint: String) {
        val oldEndpoint = store.connectedEndpointNames.entries
            .find { it.key != endpoint && NodeIdentity.matches(it.value, peerName) }?.key ?: return
        val hasLiveRole = store.activeConnections.containsKey(oldEndpoint) || store.activeServerConnections.containsKey(oldEndpoint)
        if (hasLiveRole) return
        AppLogger.d("BLE_MESH", "GHOST EVICTION: $peerName rotated MAC from $oldEndpoint to $endpoint. Purging ghost.")
        disconnectEndpoint(oldEndpoint)
        store.connectedEndpointIds.remove(oldEndpoint)
        store.connectedEndpointNames.remove(oldEndpoint)
        store.endpointLastSeen.remove(oldEndpoint)
        store.endpointNodeIds.remove(oldEndpoint)
        later { onDisconnected(oldEndpoint) }
    }

    fun admitOrElect(endpoint: String, peerName: String, advertisement: BleAdvertisement, now: Long) {
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks()) {
            val reason = BleAdmissionReason.CAPACITY_LIMIT
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = advertisement.directConnections,
                peerScore = advertisement.electionScore,
                hasIndirectRoute = hasIndirectRoute(peerName),
                reason = reason,
                result = BleConnectStartResult.REJECTED,
                attempts = 0,
                candidateAgeMs = (now - (store.endpointFirstSeen[endpoint] ?: now)).coerceAtLeast(0L),
                now = now
            )
            rescueOrphan(endpoint, peerName, advertisement.directConnections, directLinks, now)
            return
        }
        val localScore = electionScore()
        val isIsolated = distinctReadyPeerCount() == 0
        when {
            advertisement.electionScore.isNotEmpty() &&
                (localScore > advertisement.electionScore ||
                    (localScore == advertisement.electionScore &&
                        NodeIdentity.idOf(localName()).orEmpty() > NodeIdentity.idOf(peerName).orEmpty())) -> {
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore > ${advertisement.electionScore}. Queuing bootstrap connection.")
                val candidate = enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
                recordDecision(
                    peerName = peerName,
                    endpoint = endpoint,
                    peerConnections = advertisement.directConnections,
                    peerScore = advertisement.electionScore,
                    hasIndirectRoute = hasIndirectRoute(peerName),
                    reason = BleAdmissionReason.QUEUED_INITIATOR,
                    result = BleConnectStartResult.DEFERRED,
                    attempts = candidate.attempts,
                    candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                    now = now
                )
            }
            advertisement.electionScore.isEmpty() && localName() > peerName -> {
                AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: ${localName()} > $peerName. Queuing bootstrap connection.")
                val candidate = enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
                recordDecision(
                    peerName = peerName,
                    endpoint = endpoint,
                    peerConnections = advertisement.directConnections,
                    peerScore = advertisement.electionScore,
                    hasIndirectRoute = hasIndirectRoute(peerName),
                    reason = BleAdmissionReason.QUEUED_INITIATOR,
                    result = BleConnectStartResult.DEFERRED,
                    attempts = candidate.attempts,
                    candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                    now = now
                )
            }
            else -> {
                if (isIsolated) {
                    AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Isolated node yielding with fallback watchdog.")
                    val candidate = enqueueCandidate(endpoint, peerName, advertisement.directConnections, now, initialDelayMs = FALLBACK_INITIATOR_DELAY_MS)
                    recordDecision(
                        peerName = peerName,
                        endpoint = endpoint,
                        peerConnections = advertisement.directConnections,
                        peerScore = advertisement.electionScore,
                        hasIndirectRoute = hasIndirectRoute(peerName),
                        reason = BleAdmissionReason.ELECTION_YIELD,
                        result = BleConnectStartResult.DEFERRED,
                        attempts = candidate.attempts,
                        candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                        now = now
                    )
                } else {
                    removeCandidate(peerName)
                    AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Yielding.")
                    recordDecision(
                        peerName = peerName,
                        endpoint = endpoint,
                        peerConnections = advertisement.directConnections,
                        peerScore = advertisement.electionScore,
                        hasIndirectRoute = hasIndirectRoute(peerName),
                        reason = BleAdmissionReason.ELECTION_YIELD,
                        result = BleConnectStartResult.DEFERRED,
                        attempts = 0,
                        candidateAgeMs = (now - (store.endpointFirstSeen[endpoint] ?: now)).coerceAtLeast(0L),
                        now = now
                    )
                }
            }
        }
    }
}
