package com.example.testresqmesh.core.network.bluetooth

import android.os.Handler
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.utils.AppLogger

/** Applies discovery identity, capacity, orphan-rescue, and initiator-election policy. */
class BlePeerAdmissionController(
    private val store: BleStateStore,
    private val handler: Handler,
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
    private val onPulse: () -> Unit
) {
    private data class BootstrapCandidate(
        val identity: String,
        var endpoint: String,
        var peerName: String,
        var peerConnections: Int,
        var nextAttemptAt: Long,
        var attempts: Int = 0
    )

    private val candidates = linkedMapOf<String, BootstrapCandidate>()
    private var drainScheduled = false

    fun handle(advertisement: BleAdvertisement) {
        val peerName = advertisement.peerName
        val endpoint = advertisement.endpointId
        if (isBlocked(peerName) || NodeIdentity.matches(peerName, localName())) {
            removeCandidate(peerName)
            return
        }

        evictRotatedGhost(peerName, endpoint)
        val now = System.currentTimeMillis()
        store.endpointLastSeen[endpoint] = now
        store.endpointLastScore[endpoint] = advertisement.electionScore
        if (advertisement.nodeId.isNotEmpty()) store.endpointNodeIds[endpoint] = advertisement.nodeId
        store.endpointFirstSeen.putIfAbsent(endpoint, now)
        if (store.connectedEndpointIds.add(endpoint)) {
            store.connectedEndpointNames[endpoint] = peerName
            handler.post {
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

        val alreadyConnected = hasLinkToIdentity(peerName) ||
            store.activeConnections.containsKey(endpoint) || store.activeServerConnections.containsKey(endpoint)
        if (alreadyConnected) {
            removeCandidate(peerName)
            return
        }

        // A routed peer normally stays routed to avoid eagerly turning every visible mesh hop into
        // a redundant direct ACL. The exception is bootstrap/recovery: when no payload-ready direct
        // neighbor remains, a nearby unblocked routed peer may restore a direct path. Explicit UI
        // "Connect Directly" uses the same downstream block and duplicate guards.
        if (hasIndirectRoute(peerName) && hasPayloadReadyDirectLink()) {
            AppLogger.d("BLE_MESH", "Deferring direct upgrade to $peerName; healthy payload-ready direct link exists")
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
            .find { NodeIdentity.matches(it.value, peerName) }?.key ?: return
        if (oldEndpoint == endpoint) return
        if (store.activeConnections.containsKey(oldEndpoint) || store.activeServerConnections.containsKey(oldEndpoint)) return
        AppLogger.d("BLE_MESH", "GHOST EVICTION: $peerName rotated MAC from $oldEndpoint to $endpoint. Purging ghost.")
        store.connectedEndpointIds.remove(oldEndpoint)
        store.connectedEndpointNames.remove(oldEndpoint)
        store.endpointLastSeen.remove(oldEndpoint)
        store.endpointNodeIds.remove(oldEndpoint)
        handler.post { onDisconnected(oldEndpoint) }
    }

    private fun admitOrElect(endpoint: String, peerName: String, advertisement: BleAdvertisement, now: Long) {
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks() || (directLinks >= 2 && advertisement.directConnections > 0)) {
            rescueOrphan(endpoint, peerName, advertisement.directConnections, directLinks, now)
            return
        }
        val localScore = electionScore()
        when {
            advertisement.electionScore.isNotEmpty() && localScore > advertisement.electionScore -> {
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore > ${advertisement.electionScore}. Queuing bootstrap connection.")
                enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
            }
            advertisement.electionScore.isEmpty() && localName() > peerName -> {
                AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: ${localName()} > $peerName. Queuing bootstrap connection.")
                enqueueCandidate(endpoint, peerName, advertisement.directConnections, now)
            }
            else -> {
                removeCandidate(peerName)
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Yielding.")
            }
        }
    }

    private fun enqueueCandidate(endpoint: String, peerName: String, peerConnections: Int, now: Long) {
        val identity = candidateIdentity(peerName, endpoint)
        val candidate = candidates[identity]
        if (candidate == null) {
            candidates[identity] = BootstrapCandidate(
                identity = identity,
                endpoint = endpoint,
                peerName = peerName,
                peerConnections = peerConnections,
                nextAttemptAt = now + (MIN_CONNECTION_JITTER_MS..MAX_CONNECTION_JITTER_MS).random()
            )
            AppLogger.d("BLE_MESH", "Bootstrap candidate queued for $peerName")
        } else {
            candidate.endpoint = endpoint
            candidate.peerName = peerName
            candidate.peerConnections = peerConnections
        }
        scheduleDrain()
    }

    private fun scheduleDrain(delayMs: Long = 0L) {
        if (drainScheduled) return
        drainScheduled = true
        handler.postDelayed({
            drainScheduled = false
            drainCandidates()
        }, delayMs)
    }

    private fun drainCandidates() {
        val now = System.currentTimeMillis()
        candidates.values.removeAll { candidate ->
            isBlocked(candidate.peerName) ||
                hasReadyLinkToIdentity(candidate.peerName) ||
                (hasIndirectRoute(candidate.peerName) && hasPayloadReadyDirectLink())
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
            scheduleDrain(BleBootstrapRetryPolicy.ACTIVE_SETUP_RECHECK_MS)
            return
        }
        val directLinks = directLinkCount()
        if (directLinks >= maxDirectLinks() ||
            (directLinks >= 2 && candidate.peerConnections > 0)) {
            candidates.remove(candidate.identity)
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
                scheduleDrain(BleBootstrapRetryPolicy.SETUP_PROGRESS_WINDOW_MS)
            }
            BleConnectStartResult.DEFERRED -> {
                candidate.nextAttemptAt = now + BleBootstrapRetryPolicy.BUSY_RETRY_MS
                AppLogger.d("BLE_MESH", "Bootstrap setup queued for ${candidate.peerName}; radio is busy")
                scheduleDrain(BleBootstrapRetryPolicy.BUSY_RETRY_MS)
            }
            BleConnectStartResult.REJECTED -> {
                candidate.attempts += 1
                val delay = BleBootstrapRetryPolicy.retryDelay(candidate.attempts)
                candidate.nextAttemptAt = now + delay
                AppLogger.d("BLE_MESH", "Bootstrap setup rejected for ${candidate.peerName}; retry in ${delay}ms")
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
        if (now - firstDetected <= ORPHAN_RESCUE_DELAY_MS) return
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
        handler.post { onDisconnected(lruEndpoint) }
        connect(endpoint, peerName)
    }

    private companion object {
        const val ORPHAN_RESCUE_DELAY_MS = 5_000L
        const val MIN_CONNECTION_JITTER_MS = 100L
        const val MAX_CONNECTION_JITTER_MS = 1_000L
    }
}
