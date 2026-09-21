package com.example.testresqmesh.core.network.bluetooth

import android.os.Handler
import androidx.annotation.VisibleForTesting
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.utils.AppLogger
import com.example.testresqmesh.core.utils.TerminalLogEntry

interface BleAdmissionScheduler {
    fun post(action: () -> Unit)
    fun postDelayed(delayMs: Long, action: () -> Unit)
}

class HandlerAdmissionScheduler(private val handler: Handler) : BleAdmissionScheduler {
    override fun post(action: () -> Unit) { handler.post(action) }
    override fun postDelayed(delayMs: Long, action: () -> Unit) { handler.postDelayed(action, delayMs) }
}

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
    private val disconnectEndpoint: (String) -> Unit = {}
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
        disconnectEndpoint: (String) -> Unit = {}
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
        disconnectEndpoint = disconnectEndpoint
    )

    private data class BootstrapCandidate(
        val identity: String,
        var endpoint: String,
        var peerName: String,
        var peerConnections: Int,
        var nextAttemptAt: Long,
        var attempts: Int = 0,
        val enqueuedAt: Long = System.currentTimeMillis()
    )

    private val candidates = linkedMapOf<String, BootstrapCandidate>()
    private var drainScheduled = false

    fun handle(advertisement: BleAdvertisement) {
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

        evictRotatedGhost(peerName, endpoint)
        store.endpointLastSeen[endpoint] = now
        store.endpointLastScore[endpoint] = advertisement.electionScore
        if (advertisement.nodeId.isNotEmpty()) store.endpointNodeIds[endpoint] = advertisement.nodeId
        store.endpointFirstSeen.putIfAbsent(endpoint, now)
        if (store.connectedEndpointIds.add(endpoint)) {
            store.connectedEndpointNames[endpoint] = peerName
            scheduler.post {
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
            val isOldLinkSilent = (now - lastInbound) > 8_000L
            val isOldLinkAged = (now - establishTime) > 8_000L

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
                scheduler.post { onDisconnected(existingEndpoint) }
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
        scheduler.post { onDisconnected(oldEndpoint) }
    }

    private fun admitOrElect(endpoint: String, peerName: String, advertisement: BleAdvertisement, now: Long) {
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks() || (directLinks >= 2 && advertisement.directConnections > 0)) {
            val reason = if (directLinks >= maxDirectLinks()) {
                BleAdmissionReason.CAPACITY_LIMIT
            } else {
                BleAdmissionReason.TWO_LINK_PEER_CONNECTED
            }
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
            advertisement.electionScore.isNotEmpty() && localScore > advertisement.electionScore -> {
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

    private fun enqueueCandidate(
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

    private fun scheduleDrain(delayMs: Long = 0L) {
        if (drainScheduled) return
        drainScheduled = true
        scheduler.postDelayed(delayMs) {
            drainScheduled = false
            drainCandidates()
        }
    }

    @VisibleForTesting
    internal fun drainCandidates() {
        val now = clock()
        val staleCandidates = candidates.values.filter { candidate ->
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
        if (directLinks >= maxDirectLinks() ||
            (directLinks >= 2 && candidate.peerConnections > 0)) {
            candidates.remove(candidate.identity)
            val limitReason = if (directLinks >= maxDirectLinks()) {
                BleAdmissionReason.CAPACITY_LIMIT
            } else {
                BleAdmissionReason.TWO_LINK_PEER_CONNECTED
            }
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

    private fun removeCandidate(peerName: String) {
        candidates.remove(candidateIdentity(peerName, ""))
    }

    private fun candidateIdentity(peerName: String, endpoint: String): String =
        NodeIdentity.idOf(peerName)?.let { "node:$it" } ?: "endpoint:${NodeIdentity.key(peerName).ifEmpty { endpoint }}"

    private fun rescueOrphan(endpoint: String, peerName: String, peerConnections: Int, directLinks: Int, now: Long) {
        if (peerConnections != 0 || directLinks < maxDirectLinks()) {
            store.orphanDetectionTime.remove(endpoint)
            return
        }
        val firstDetected = store.orphanDetectionTime.putIfAbsent(endpoint, now) ?: return
        if (now - firstDetected <= ORPHAN_RESCUE_DELAY_MS) {
            recordDecision(
                peerName = peerName,
                endpoint = endpoint,
                peerConnections = peerConnections,
                peerScore = store.endpointLastScore[endpoint].orEmpty(),
                hasIndirectRoute = hasIndirectRoute(peerName),
                reason = BleAdmissionReason.ORPHAN_EVALUATING,
                result = BleConnectStartResult.DEFERRED,
                attempts = 0,
                candidateAgeMs = (now - firstDetected).coerceAtLeast(0L),
                now = now
            )
            return
        }
        AppLogger.d("BLE_MESH", "Orphan Preemption: Found orphan $peerName. Dropping weakest link to rescue.")
        val lruEndpoint = store.connectionInteractionTimes
            .filterKeys { store.activeConnections.containsKey(it) }
            .filterKeys { store.pendingQueues[it]?.isEmpty() != false }
            .minByOrNull { it.value }?.key ?: return
        try {
            store.activeConnections[lruEndpoint]?.disconnect()
            store.activeConnections[lruEndpoint]?.close()
        } catch (e: SecurityException) {
            AppLogger.d("BLE_MESH", "Orphan preemption could not close $lruEndpoint: BLUETOOTH_CONNECT was revoked")
        }
        store.activeConnections.remove(lruEndpoint)
        store.pendingQueues.remove(lruEndpoint)
        store.isWriting.remove(lruEndpoint)
        store.chunkBuffers.remove(lruEndpoint)
        store.connectionInteractionTimes.remove(lruEndpoint)
        scheduler.post { onDisconnected(lruEndpoint) }
        connect(endpoint, peerName)
    }

    private fun recordDecision(
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
                else -> "tie"
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

    companion object {
        const val ORPHAN_RESCUE_DELAY_MS = 5_000L
        const val MIN_CONNECTION_JITTER_MS = 100L
        const val MAX_CONNECTION_JITTER_MS = 1_000L
        const val FALLBACK_INITIATOR_DELAY_MS = 2_500L
        val macRegex = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    }
}
