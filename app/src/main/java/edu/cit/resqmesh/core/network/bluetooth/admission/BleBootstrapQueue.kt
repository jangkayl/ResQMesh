package edu.cit.resqmesh.core.network.bluetooth.admission

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.bluetooth.BleAdvertisement
import edu.cit.resqmesh.core.network.bluetooth.BleAdmissionReason
import edu.cit.resqmesh.core.network.bluetooth.BleConnectStartResult
import edu.cit.resqmesh.core.network.bluetooth.BleBootstrapRetryPolicy
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.CANDIDATE_FRESH_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CANDIDATES
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.BRIDGE_COOLDOWN_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.ORPHAN_RESCUE_DELAY_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MIN_CONNECTION_JITTER_MS
import edu.cit.resqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CONNECTION_JITTER_MS

internal class BleBootstrapQueue(
    override val capabilities: BleAdmissionCapabilities,
    override val session: BleAdmissionSession,
    override val diagnostics: BleAdmissionDiagnostics,
    private val admitOrElect: (String, String, BleAdvertisement, Long) -> Unit
) : BleAdmissionContext {
    fun enqueueCandidate(
        endpoint: String,
        peerName: String,
        peerConnections: Int,
        now: Long,
        initialDelayMs: Long? = null
    ): BootstrapCandidate {
        val identity = candidateIdentity(peerName, endpoint)
        val candidate = candidates[identity]
        val delay = initialDelayMs ?: jitterMs(MIN_CONNECTION_JITTER_MS, MAX_CONNECTION_JITTER_MS)
        val result = if (candidate == null) {
            if (candidates.size >= MAX_CANDIDATES) candidates.remove(candidates.keys.first())
            val created = BootstrapCandidate(
                identity = identity,
                endpoint = endpoint,
                peerName = peerName,
                peerConnections = peerConnections,
                nextAttemptAt = now + delay,
                enqueuedAt = now
            )
            candidates[identity] = created
            AppLogger.d("BLE_MESH", "Bootstrap candidate queued for $peerName (delay=${delay}ms)")
            created
        } else {
            candidate.endpoint = endpoint
            candidate.peerName = peerName
            candidate.peerConnections = peerConnections
            candidate
        }
        scheduleDrain()
        return result
    }

    fun scheduleDrain(delayMs: Long = 0L) {
        if (drainScheduled) return
        drainScheduled = true
        later(delayMs) {
            drainScheduled = false
            drainCandidates()
        }
    }

    internal fun drainCandidates() {
        if (!isNodeActive()) return
        val now = clock()
        evictStaleCandidates(now)

        val candidate = nextDueCandidate(now)
        if (candidate == null) {
            candidates.values.minOfOrNull { it.nextAttemptAt }?.let { nextAt ->
                scheduleDrain((nextAt - now).coerceAtLeast(1L))
            }
            return
        }
        if (hasLinkToIdentity(candidate.peerName)) {
            candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.ACTIVE_SETUP_RECHECK_MS
            recordCandidateDecision(candidate, candidate.endpoint, BleAdmissionReason.ACTIVE_SETUP,
                BleConnectStartResult.DEFERRED, now)
            scheduleDrain(BleBootstrapRetryPolicy.ACTIVE_SETUP_RECHECK_MS)
            return
        }
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks()) {
            candidates.remove(candidate.identity)
            recordCandidateDecision(candidate, candidate.endpoint, BleAdmissionReason.CAPACITY_LIMIT,
                BleConnectStartResult.REJECTED, now)
            return
        }
        startCandidateSetup(candidate, now)
    }

    /** Drops candidates that went quiet, got blocked, already connected, or are reachable another way. */
    private fun evictStaleCandidates(now: Long) {
        val staleCandidates = candidates.values.filter { candidate ->
            now - (store.endpointLastSeen[candidate.endpoint] ?: 0L) > CANDIDATE_FRESH_MS ||
                isBlocked(candidate.peerName) ||
                hasReadyLinkToIdentity(candidate.peerName) ||
                (hasIndirectRoute(candidate.peerName) && hasPayloadReadyDirectLink())
        }
        for (stale in staleCandidates) {
            candidates.remove(stale.identity)
            val staleReason = when {
                isBlocked(stale.peerName) -> BleAdmissionReason.BLOCKED
                hasReadyLinkToIdentity(stale.peerName) -> BleAdmissionReason.ALREADY_CONNECTED
                else -> BleAdmissionReason.ROUTE_PRESERVED
            }
            recordCandidateDecision(stale, stale.endpoint, staleReason, BleConnectStartResult.REJECTED, now)
        }
    }

    /** The due candidate with the earliest attempt time, fewest attempts breaking ties. */
    private fun nextDueCandidate(now: Long): BootstrapCandidate? = candidates.values
        .filter { it.nextAttemptAt <= now }
        .minWithOrNull(compareBy<BootstrapCandidate> { it.nextAttemptAt }.thenBy { it.attempts })

    private fun startCandidateSetup(candidate: BootstrapCandidate, now: Long) {
        val endpoint = latestEndpointForIdentity(candidate.peerName, candidate.endpoint)
        if (endpoint != candidate.endpoint) {
            AppLogger.d("BLE_MESH", "Resolved queued endpoint for ${candidate.peerName}: ${candidate.endpoint} -> $endpoint")
            candidate.endpoint = endpoint
        }
        when (connect(endpoint, candidate.peerName)) {
            BleConnectStartResult.STARTED -> {
                candidate.attempts += 1
                candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.SETUP_PROGRESS_WINDOW_MS
                AppLogger.d("BLE_MESH", "Bootstrap setup started for ${candidate.peerName}; attempt ${candidate.attempts}")
                recordCandidateDecision(candidate, endpoint, BleAdmissionReason.DRAIN_STARTED,
                    BleConnectStartResult.STARTED, now)
                scheduleDrain(BleBootstrapRetryPolicy.SETUP_PROGRESS_WINDOW_MS)
            }
            BleConnectStartResult.DEFERRED -> {
                candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.BUSY_RETRY_MS
                AppLogger.d("BLE_MESH", "Bootstrap setup queued for ${candidate.peerName}; radio is busy")
                recordCandidateDecision(candidate, endpoint, BleAdmissionReason.DRAIN_DEFERRED,
                    BleConnectStartResult.DEFERRED, now)
                scheduleDrain(BleBootstrapRetryPolicy.BUSY_RETRY_MS)
            }
            BleConnectStartResult.REJECTED -> {
                candidate.attempts += 1
                val delay = BleBootstrapRetryPolicy.retryDelay(candidate.attempts)
                candidate.nextAttemptAt = now + delay
                AppLogger.d("BLE_MESH", "Bootstrap setup rejected for ${candidate.peerName}; retry in ${delay}ms")
                recordCandidateDecision(candidate, endpoint, BleAdmissionReason.DRAIN_REJECTED,
                    BleConnectStartResult.REJECTED, now)
                scheduleDrain(delay)
            }
        }
    }

    fun removeCandidate(peerName: String) {
        candidates.remove(candidateIdentity(peerName, ""))
    }

    fun rescueOrphan(endpoint: String, peerName: String, peerConnections: Int, directLinks: Int, now: Long) {
        if (!isOrphanRescueCandidate(endpoint, peerName, directLinks, now)) {
            store.orphanDetectionTime.remove(endpoint)
            return
        }
        val wasAbsent = !store.orphanDetectionTime.containsKey(endpoint)
        val targetRescueTime = store.orphanDetectionTime.computeIfAbsent(endpoint) {
            now + ORPHAN_RESCUE_DELAY_MS + jitterMs(1_000L, 4_000L)
        }
        if (wasAbsent) {
            val delay = (targetRescueTime - now).coerceAtLeast(0L)
            later(delay) {
                rescueOrphan(endpoint, peerName, peerConnections, directLinkCount(), clock())
            }
        }
        if (now < targetRescueTime) {
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = peerConnections,
                peerScore = store.endpointLastScore[endpoint].orEmpty(),
                hasIndirectRoute = false,
                reason = BleAdmissionReason.ORPHAN_EVALUATING,
                result = BleConnectStartResult.DEFERRED,
                attempts = 0,
                candidateAgeMs = (now - (store.endpointFirstSeen[endpoint] ?: now)).coerceAtLeast(0L),
                now = now
            )
            return
        }
        // Thundering Herd Guard: Did the peer become reachable via mesh routing during the jitter window?
        if (hasIndirectRoute(peerName) || hasLinkToIdentity(peerName)) {
            AppLogger.d("BLE_MESH", "Partition/Orphan resolution: $peerName is now reachable via mesh. Aborting preemption.")
            store.orphanDetectionTime.remove(endpoint)
            return
        }
        if (lastBridgeAt != Long.MIN_VALUE && now - lastBridgeAt < BRIDGE_COOLDOWN_MS) return
        val redundantEndpoint = idleRetirableEndpoint() ?: return
        lastBridgeAt = now
        AppLogger.d("BLE_ADMISSION", "Reclaiming a redundant GATT link for an unreachable peer")
        disconnectEndpoint(redundantEndpoint)
        store.orphanDetectionTime.remove(endpoint)
        // Run through the same serialized candidate/election lane after retirement.
        advertisements[candidateIdentity(peerName, endpoint)]?.first?.let { ad ->
            later { admitOrElect(endpoint, peerName, ad, clock()) }
        }
    }

    /**
     * An orphan is worth rescuing only while the node is active, the peer is unblocked and has no
     * routed path, this phone is at its link limit, and the peer was seen recently.
     */
    private fun isOrphanRescueCandidate(endpoint: String, peerName: String, directLinks: Int, now: Long): Boolean =
        isNodeActive() && !isBlocked(peerName) && !hasIndirectRoute(peerName) && directLinks >= maxDirectLinks() &&
            now - (store.endpointLastSeen[endpoint] ?: 0L) <= CANDIDATE_FRESH_MS

    /** The least recently used READY link that is idle on every transport and may be retired. */
    private fun idleRetirableEndpoint(): String? = store.connectionInteractionTimes
        .filterKeys { store.links.isReady(it) && store.pendingQueues[it]?.isEmpty() != false }
        .filterKeys { store.gattFlights[it]?.writing?.get() != true && store.isWriting[it]?.get() != true }
        .filterKeys { isTransportIdle(it) }
        .filterKeys { canRetireForBridge(it) }
        .minByOrNull { it.value }?.key
}
