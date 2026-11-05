package edu.cit.resqmesh.core.network.bluetooth.admission

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.model.ScanEvent
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.bluetooth.BleAdvertisement
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionReason
import edu.cit.resqmesh.core.network.bluetooth.BleConnectStartResult
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.CANDIDATE_FRESH_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CANDIDATES
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.FALLBACK_INITIATOR_DELAY_MS

private const val BLOCKED_SCAN_REPUBLISH_MS = 2_000L

internal class BleAdvertisementAdmission(
    override val capabilities: BleAdmissionCapabilities,
    override val session: BleAdmissionSession,
    override val diagnostics: BleAdmissionDiagnostics,
    private val bootstrap: () -> BleBootstrapQueue
) : BleAdmissionContext {
    fun recover() {
        if (!isNodeActive()) return
        val now = clock()
        advertisements.entries.removeAll { now - it.value.second > CANDIDATE_FRESH_MS }
        advertisements.values.toList().forEach { (ad, _) -> handle(ad, observed = false) }
        bootstrap().drainCandidates()
    }

    /**
     * A blocked peer in radio range stays visible to the UI. This only publishes the scan row and
     * refreshes its liveness so the normal 8 s scan expiry removes it; it never adds an
     * advertisement, candidate or connect attempt, and leaves link bookkeeping untouched.
     */
    private fun publishBlockedScan(advertisement: BleAdvertisement, now: Long) {
        val endpoint = advertisement.endpointId
        val previous = store.endpointLastSeen.put(endpoint, now)
        if (previous != null && now - previous < BLOCKED_SCAN_REPUBLISH_MS) return
        later {
            onScanned(
                ScanEvent(
                    endpointId = endpoint,
                    name = advertisement.peerName,
                    nodeId = advertisement.nodeId,
                    peerConnections = advertisement.directConnections,
                    peerScore = advertisement.electionScore,
                    isConnecting = false
                )
            )
        }
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
            bootstrap().removeCandidate(peerName)
            if (observed && reason == BleAdmissionReason.BLOCKED) publishBlockedScan(advertisement, now)
            return
        }

        noteAdvertisement(advertisement, observed, now)
        purgeRebootedZombie(advertisement, now)

        val alreadyConnected = hasLinkToIdentity(peerName) ||
            store.activeConnections.containsKey(endpoint) || store.activeServerConnections.containsKey(endpoint)
        if (alreadyConnected) {
            recordAdvertisementDecision(AdmissionSubject(endpoint, peerName, advertisement),
                BleAdmissionReason.ALREADY_CONNECTED, BleConnectStartResult.REJECTED, now)
            bootstrap().removeCandidate(peerName)
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
            bootstrap().removeCandidate(peerName)
            return
        }
        if (hasIndirectRoute(peerName)) {
            AppLogger.d("BLE_MESH", "Direct bootstrap candidate $peerName; no payload-ready direct link remains")
        }
        admitOrElect(endpoint, peerName, advertisement, now)
    }

    /** Stores the advertisement and endpoint metadata, and publishes a scan row for a new endpoint. */
    private fun noteAdvertisement(advertisement: BleAdvertisement, observed: Boolean, now: Long) {
        val peerName = advertisement.peerName
        val endpoint = advertisement.endpointId
        if (observed) {
            advertisements[candidateIdentity(peerName, endpoint)] = advertisement to now
            while (advertisements.size > MAX_CANDIDATES) advertisements.remove(advertisements.keys.first())
        }
        evictRotatedGhost(peerName, endpoint, advertisement.nodeId)
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
    }

    /**
     * Checks for zombie/ghost links from a rebooted peer before checking alreadyConnected.
     * A rebooted peer arrives on a new endpoint MAC and advertises directConnections == 0.
     * If our existing socket to this peer identity is not ready, silent, or peer advertises 0,
     * it is a dead zombie that must be purged to unblock new connection setup.
     */
    private fun purgeRebootedZombie(advertisement: BleAdvertisement, now: Long) {
        val existingEndpoint = otherEndpointForPeer(advertisement) ?: return
        if (!isZombieLink(existingEndpoint, advertisement, now)) return
        AppLogger.d("BLE_MESH", "Purging zombie link $existingEndpoint for rebooted peer ${advertisement.peerName} " +
            "(new endpoint ${advertisement.endpointId})")
        forgetEndpoint(existingEndpoint)
    }

    /** Another endpoint already known for this peer, by name first and then by advertised node ID. */
    private fun otherEndpointForPeer(advertisement: BleAdvertisement): String? {
        val endpoint = advertisement.endpointId
        val existingEndpoint = store.connectedEndpointNames.entries
            .find { it.key != endpoint && NodeIdentity.matches(it.value, advertisement.peerName) }?.key
            ?: (advertisement.nodeId.takeIf { it.isNotEmpty() }?.let { id ->
                store.endpointNodeIds.entries.find { it.key != endpoint && it.value == id }?.key
            })
        return existingEndpoint?.takeIf { it != endpoint }
    }

    /**
     * Only evict as a rebooted zombie if the existing socket is genuinely silent/aged
     * and the peer advertises directConnections == 0. A newly connecting or actively
     * communicating socket must not be torn down simply because the peer's peripheral
     * advertising MAC differs from its central connection MAC.
     */
    private fun isZombieLink(existingEndpoint: String, advertisement: BleAdvertisement, now: Long): Boolean {
        val hasLiveRole = store.activeConnections.containsKey(existingEndpoint) ||
            store.activeServerConnections.containsKey(existingEndpoint)
        val establishTime = store.connectionEstablishTime[existingEndpoint] ?: 0L
        val lastInbound = store.connectionInteractionTimes[existingEndpoint] ?: establishTime
        val isOldLinkSilent = (now - lastInbound) > ZOMBIE_LINK_AGE_MS
        val isOldLinkAged = (now - establishTime) > ZOMBIE_LINK_AGE_MS
        return hasLiveRole && isOldLinkSilent && isOldLinkAged && advertisement.directConnections == 0 &&
            mayReplaceLink(existingEndpoint, advertisement.peerName, advertisement.nodeId)
    }

    /**
     * An identity claimed only in an advertisement (unauthenticated) may replace an existing link
     * only when that link's identity is not admitted, or the claim equals the identity it was admitted
     * under. This is what keeps legitimate MAC rotation working: the same admitted node reappears on a
     * new address with the same node ID. Unknown admitted ID fails closed.
     */
    private fun mayReplaceLink(oldEndpoint: String, peerName: String, claimedNodeId: String): Boolean {
        if (!store.links.isIdentityAdmitted(oldEndpoint)) return true
        val admittedId = BleLinkRole.entries
            .firstNotNullOfOrNull { store.links.current(oldEndpoint, it)?.peerNodeId?.takeIf(String::isNotBlank) }
            ?: NodeIdentity.idOf(store.connectedEndpointNames[oldEndpoint])
            ?: store.endpointNodeIds[oldEndpoint]
        val claimed = claimedNodeId.ifEmpty { NodeIdentity.idOf(peerName).orEmpty() }
        val allowed = admittedId != null && claimed.isNotEmpty() && admittedId.equals(claimed, ignoreCase = true)
        if (!allowed) {
            AppLogger.d("BLE_MESH", "ADV_EVICT_BLOCKED endpoint=$oldEndpoint reason=admitted_identity_mismatch")
        }
        return allowed
    }

    private fun evictRotatedGhost(peerName: String, endpoint: String, claimedNodeId: String) {
        val oldEndpoint = store.connectedEndpointNames.entries
            .find { it.key != endpoint && NodeIdentity.matches(it.value, peerName) }?.key ?: return
        val hasLiveRole = store.activeConnections.containsKey(oldEndpoint) || store.activeServerConnections.containsKey(oldEndpoint)
        if (hasLiveRole || !mayReplaceLink(oldEndpoint, peerName, claimedNodeId)) return
        AppLogger.d("BLE_MESH", "GHOST EVICTION: $peerName rotated MAC from $oldEndpoint to $endpoint. Purging ghost.")
        forgetEndpoint(oldEndpoint)
    }

    fun admitOrElect(endpoint: String, peerName: String, advertisement: BleAdvertisement, now: Long) {
        val subject = AdmissionSubject(endpoint, peerName, advertisement)
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks()) {
            recordAdvertisementDecision(subject, BleAdmissionReason.CAPACITY_LIMIT, BleConnectStartResult.REJECTED, now)
            bootstrap().rescueOrphan(endpoint, peerName, advertisement.directConnections, directLinks, now)
            return
        }
        val localScore = electionScore()
        val isIsolated = distinctReadyPeerCount() == 0
        when {
            winsScoreElection(localScore, advertisement.electionScore, localName(), peerName) -> {
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore > ${advertisement.electionScore}. Queuing bootstrap connection.")
                val candidate = bootstrap().enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
                recordQueuedDecision(subject, candidate, BleAdmissionReason.QUEUED_INITIATOR, now)
            }
            advertisement.electionScore.isEmpty() && localName() > peerName -> {
                AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: ${localName()} > $peerName. Queuing bootstrap connection.")
                val candidate = bootstrap().enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
                recordQueuedDecision(subject, candidate, BleAdmissionReason.QUEUED_INITIATOR, now)
            }
            isIsolated -> {
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Isolated node yielding with fallback watchdog.")
                val candidate = bootstrap().enqueueCandidate(endpoint, peerName, advertisement.directConnections, now,
                    initialDelayMs = FALLBACK_INITIATOR_DELAY_MS)
                recordQueuedDecision(subject, candidate, BleAdmissionReason.ELECTION_YIELD, now)
            }
            else -> {
                bootstrap().removeCandidate(peerName)
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Yielding.")
                recordAdvertisementDecision(subject, BleAdmissionReason.ELECTION_YIELD,
                    BleConnectStartResult.DEFERRED, now)
            }
        }
    }

}

/** Higher election score initiates; equal scores fall back to the higher node ID. */
private fun winsScoreElection(localScore: String, peerScore: String, localName: String, peerName: String): Boolean =
    peerScore.isNotEmpty() &&
        (localScore > peerScore ||
            (localScore == peerScore &&
                NodeIdentity.idOf(localName).orEmpty() > NodeIdentity.idOf(peerName).orEmpty()))

/** An existing link this silent and this old may be a rebooted peer's zombie socket. */
private const val ZOMBIE_LINK_AGE_MS = 15_000L
