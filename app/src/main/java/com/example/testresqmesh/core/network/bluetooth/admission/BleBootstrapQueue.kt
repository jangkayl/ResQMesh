package com.example.testresqmesh.core.network.bluetooth.admission

import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.network.bluetooth.BleAdvertisement
import com.example.testresqmesh.core.network.bluetooth.BleAdmissionReason
import com.example.testresqmesh.core.network.bluetooth.BleConnectStartResult
import com.example.testresqmesh.core.network.bluetooth.BleBootstrapRetryPolicy
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.CANDIDATE_FRESH_MS
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CANDIDATES
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.BRIDGE_COOLDOWN_MS
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.ORPHAN_RESCUE_DELAY_MS
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MIN_CONNECTION_JITTER_MS
import com.example.testresqmesh.core.network.bluetooth.admission.BleAdmissionConstants.MAX_CONNECTION_JITTER_MS

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
            recordDecision(
                peerName = stale.peerName,
                endpoint = stale.endpoint,
                peerConnections = stale.peerConnections,
                peerScore = store.endpointLastScore[stale.endpoint].orEmpty(),
                hasIndirectRoute = hasIndirectRoute(stale.peerName),
                reason = staleReason,
                result = BleConnectStartResult.REJECTED,
                attempts = stale.attempts,
                candidateAgeMs = (now - stale.enqueuedAt).coerceAtLeast(0L),
                now = now
            )
        }

        val candidate = candidates.values
            .filter { it.nextAttemptAt <= now }
            .minWithOrNull(compareBy<BootstrapCandidate> { it.nextAttemptAt }.thenBy { it.attempts })
        if (candidate == null) {
            candidates.values.minOfOrNull { it.nextAttemptAt }?.let { nextAt ->
                scheduleDrain((nextAt - now).coerceAtLeast(1L))
            }
            return
        }
        if (hasLinkToIdentity(candidate.peerName)) {
            candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.ACTIVE_SETUP_RECHECK_MS
            recordDecision(
                peerName = candidate.peerName,
                endpoint = candidate.endpoint,
                peerConnections = candidate.peerConnections,
                peerScore = store.endpointLastScore[candidate.endpoint].orEmpty(),
                hasIndirectRoute = hasIndirectRoute(candidate.peerName),
                reason = BleAdmissionReason.ACTIVE_SETUP,
                result = BleConnectStartResult.DEFERRED,
                attempts = candidate.attempts,
                candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                now = now
            )
            scheduleDrain(BleBootstrapRetryPolicy.ACTIVE_SETUP_RECHECK_MS)
            return
        }
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks()) {
            candidates.remove(candidate.identity)
            val limitReason = BleAdmissionReason.CAPACITY_LIMIT
            recordDecision(
                peerName = candidate.peerName,
                endpoint = candidate.endpoint,
                peerConnections = candidate.peerConnections,
                peerScore = store.endpointLastScore[candidate.endpoint].orEmpty(),
                hasIndirectRoute = hasIndirectRoute(candidate.peerName),
                reason = limitReason,
                result = BleConnectStartResult.REJECTED,
                attempts = candidate.attempts,
                candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                now = now
            )
            return
        }
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
                recordDecision(
                    peerName = candidate.peerName,
                    endpoint = endpoint,
                    peerConnections = candidate.peerConnections,
                    peerScore = store.endpointLastScore[endpoint].orEmpty(),
                    hasIndirectRoute = hasIndirectRoute(candidate.peerName),
                    reason = BleAdmissionReason.DRAIN_STARTED,
                    result = BleConnectStartResult.STARTED,
                    attempts = candidate.attempts,
                    candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                    now = now
                )
                scheduleDrain(BleBootstrapRetryPolicy.SETUP_PROGRESS_WINDOW_MS)
            }
            BleConnectStartResult.DEFERRED -> {
                candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.BUSY_RETRY_MS
                AppLogger.d("BLE_MESH", "Bootstrap setup queued for ${candidate.peerName}; radio is busy")
                recordDecision(
                    peerName = candidate.peerName,
                    endpoint = endpoint,
                    peerConnections = candidate.peerConnections,
                    peerScore = store.endpointLastScore[endpoint].orEmpty(),
                    hasIndirectRoute = hasIndirectRoute(candidate.peerName),
                    reason = BleAdmissionReason.DRAIN_DEFERRED,
                    result = BleConnectStartResult.DEFERRED,
                    attempts = candidate.attempts,
                    candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                    now = now
                )
                scheduleDrain(BleBootstrapRetryPolicy.BUSY_RETRY_MS)
            }
            BleConnectStartResult.REJECTED -> {
                candidate.attempts += 1
                val delay = BleBootstrapRetryPolicy.retryDelay(candidate.attempts)
                candidate.nextAttemptAt = now + delay
                AppLogger.d("BLE_MESH", "Bootstrap setup rejected for ${candidate.peerName}; retry in ${delay}ms")
                recordDecision(
                    peerName = candidate.peerName,
                    endpoint = endpoint,
                    peerConnections = candidate.peerConnections,
                    peerScore = store.endpointLastScore[endpoint].orEmpty(),
                    hasIndirectRoute = hasIndirectRoute(candidate.peerName),
                    reason = BleAdmissionReason.DRAIN_REJECTED,
                    result = BleConnectStartResult.REJECTED,
                    attempts = candidate.attempts,
                    candidateAgeMs = (now - candidate.enqueuedAt).coerceAtLeast(0L),
                    now = now
                )
                scheduleDrain(delay)
            }
        }
    }

    fun removeCandidate(peerName: String) {
        candidates.remove(candidateIdentity(peerName, ""))
    }

    fun rescueOrphan(endpoint: String, peerName: String, peerConnections: Int, directLinks: Int, now: Long) {
        if (!isNodeActive() || isBlocked(peerName) || hasIndirectRoute(peerName) || directLinks < maxDirectLinks() ||
            now - (store.endpointLastSeen[endpoint] ?: 0L) > CANDIDATE_FRESH_MS) {
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
        val redundantEndpoint = store.connectionInteractionTimes
            .filterKeys { store.links.isReady(it) && store.pendingQueues[it]?.isEmpty() != false }
            .filterKeys { store.gattFlights[it]?.writing?.get() != true && store.isWriting[it]?.get() != true }
            .filterKeys { isTransportIdle(it) }
            .filterKeys { canRetireForBridge(it) }
            .minByOrNull { it.value }?.key ?: return
        lastBridgeAt = now
        AppLogger.d("BLE_ADMISSION", "Reclaiming a redundant GATT link for an unreachable peer")
        disconnectEndpoint(redundantEndpoint)
        store.orphanDetectionTime.remove(endpoint)
        // Run through the same serialized candidate/election lane after retirement.
        advertisements[candidateIdentity(peerName, endpoint)]?.first?.let { ad ->
            later { admitOrElect(endpoint, peerName, ad, clock()) }
        }
    }
}
