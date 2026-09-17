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
    private val directLinkCount: () -> Int,
    private val maxDirectLinks: () -> Int,
    private val electionScore: () -> String,
    private val latestEndpointForIdentity: (String, String) -> String,
    private val connect: (String, String) -> Unit,
    private val onScanned: (ScanEvent) -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onPulse: () -> Unit
) {
    fun handle(advertisement: BleAdvertisement) {
        val peerName = advertisement.peerName
        val endpoint = advertisement.endpointId
        if (isBlocked(peerName) || NodeIdentity.matches(peerName, localName())) return

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
        if (alreadyConnected) return

        // A routed peer normally stays routed to avoid eagerly turning every visible mesh hop into
        // a redundant direct ACL. The exception is bootstrap/recovery: when no payload-ready direct
        // neighbor remains, a nearby unblocked routed peer may restore a direct path. Explicit UI
        // "Connect Directly" uses the same downstream block and duplicate guards.
        if (hasIndirectRoute(peerName) && hasPayloadReadyDirectLink()) {
            AppLogger.d("BLE_MESH", "Deferring direct upgrade to $peerName; healthy payload-ready direct link exists")
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
        val lastAttempt = store.connectionAttempts[endpoint] ?: 0L
        if (now - lastAttempt <= CONNECT_COOLDOWN_MS) return
        store.connectionAttempts[endpoint] = now
        val localScore = electionScore()
        when {
            advertisement.electionScore.isNotEmpty() && localScore > advertisement.electionScore -> {
                AppLogger.d("BLE_MESH", "Battery Master Election: $localScore > ${advertisement.electionScore}. Initiating connection with jitter.")
                handler.postDelayed({
                    val latestEndpoint = latestEndpointForIdentity(peerName, endpoint)
                    if (latestEndpoint != endpoint) {
                        AppLogger.d("BLE_MESH", "Resolved rotated endpoint for $peerName: $endpoint -> $latestEndpoint")
                    }
                    connect(latestEndpoint, peerName)
                }, (MIN_CONNECTION_JITTER_MS..MAX_CONNECTION_JITTER_MS).random())
            }
            advertisement.electionScore.isEmpty() && localName() > peerName -> {
                AppLogger.d("BLE_MESH", "Legacy Alphabetical Rule: ${localName()} > $peerName. Initiating connection.")
                connect(endpoint, peerName)
            }
            else -> AppLogger.d("BLE_MESH", "Battery Master Election: $localScore <= ${advertisement.electionScore}. Yielding.")
        }
    }

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
        store.activeConnections[lruEndpoint]?.disconnect()
        store.activeConnections[lruEndpoint]?.close()
        store.activeConnections.remove(lruEndpoint)
        store.pendingQueues.remove(lruEndpoint)
        store.isWriting.remove(lruEndpoint)
        store.chunkBuffers.remove(lruEndpoint)
        store.connectionInteractionTimes.remove(lruEndpoint)
        handler.post { onDisconnected(lruEndpoint) }
        connect(endpoint, peerName)
    }

    private companion object {
        const val CONNECT_COOLDOWN_MS = 15_000L
        const val ORPHAN_RESCUE_DELAY_MS = 5_000L
        const val MIN_CONNECTION_JITTER_MS = 100L
        const val MAX_CONNECTION_JITTER_MS = 1_000L
    }
}
