package edu.cit.resqmesh.core.network.bluetooth

import android.os.Handler
import edu.cit.resqmesh.core.network.bluetooth.state.BleLivenessPolicy
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore
import edu.cit.resqmesh.core.network.bluetooth.state.HeartbeatChallenge
import edu.cit.resqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkRole
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.logging.AppLogger

/** Runs bounded liveness, stale-link, scan-expiry, and connect-lock recovery policy. */
class BleLifecycleSupervisor(
    private val store: BleStateStore,
    private val handler: Handler,
    private val heartbeats: HeartbeatCoordinator,
    private val heartbeatAckTimeoutMs: Long,
    private val connectLockMaxHoldMs: Long,
    private val releaseConnectLock: () -> Unit,
    private val startHeartbeat: (String) -> Unit,
    private val disconnectClient: (String) -> Unit,
    private val disconnectServer: (String) -> Unit,
    private val onLivenessChanged: (String, Boolean) -> Unit,
    private val onExpiredScan: (String) -> Unit,
    private val onPulse: () -> Unit
) {
    private val runnable = object : Runnable {
        override fun run() {
            checkNow()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun checkNow() {
        if (!store.isNodeActive.get()) return
        val now = System.currentTimeMillis()
        releaseStuckConnectLock(now)
        val activeEndpoints = (store.activeConnections.keys + store.activeServerConnections.keys).toSet()
        activeEndpoints.forEach { endpoint -> superviseLink(endpoint, now) }
        expireScans(activeEndpoints, now)
        onPulse()
    }

    private fun releaseStuckConnectLock(now: Long) {
        val lockAge = now - store.connectLockAcquiredAt
        if (store.isConnecting.get() && store.connectLockAcquiredAt > 0L && lockAge > connectLockMaxHoldMs) {
            AppLogger.d("BLE_MESH", "Connect lock stuck for ${lockAge}ms on ${store.connectingMacAddress}. Force-releasing.")
            releaseConnectLock()
        }
    }

    /** Liveness, heartbeat start and stale retirement for one endpoint with an active GATT role. */
    private fun superviseLink(endpoint: String, now: Long) {
        val readyLinks = BleLinkRole.entries.mapNotNull { store.links.current(endpoint, it) }
            .filter { it.state == BleLinkState.READY }
        // Setup and first identity have their own deadlines. An endpoint map is not READY.
        if (readyLinks.isEmpty()) return
        val awaitingFirstIdentity = readyLinks.none { it.identityAdmitted } &&
            readyLinks.all { now - it.readyAt < FIRST_IDENTITY_GRACE_MS }
        if (awaitingFirstIdentity) return
        val lastInbound = store.connectionInteractionTimes[endpoint]
            ?: store.connectionEstablishTime.computeIfAbsent(endpoint) { now }
        onLivenessChanged(endpoint, !BleLivenessPolicy.isUnresponsive(lastInbound, now))
        if (!store.activeL2capSockets.containsKey(endpoint) &&
            now - lastInbound >= BleLivenessPolicy.UNRESPONSIVE_AFTER_MS && !heartbeats.contains(endpoint)) {
            startHeartbeat(endpoint)
        }
        if (BleLivenessPolicy.isStale(lastInbound, now)) {
            retireOrWaitOnStaleLink(endpoint, lastInbound, now)
        } else {
            store.endpointLastSeen[endpoint] = now
        }
    }

    /** A stale link is kept while owned writes progress or a fresh heartbeat probe is outstanding. */
    private fun retireOrWaitOnStaleLink(endpoint: String, lastInbound: Long, now: Long) {
        val probe = heartbeats.pending(endpoint)
        val acknowledgedProgress = BleLinkRole.entries.maxOf { role ->
            store.links.current(endpoint, role)?.lastAcknowledgedWriteAt ?: 0L
        }.coerceAtLeast(l2capOutboundProgress(endpoint))
        if (BleLivenessPolicy.hasRecentOutboundProgress(acknowledgedProgress, now)) {
            AppLogger.d("BLE_MESH", "Deferring silent-link retirement on $endpoint while owned writes are progressing")
        } else if (probe != null && isProbePending(probe, now)) {
            AppLogger.d("BLE_MESH", "Waiting for generation-bound GATT heartbeat result on $endpoint")
        } else {
            AppLogger.d("BLE_MESH", "No inbound progress from $endpoint for ${now - lastInbound}ms. Retiring stale GATT roles.")
            heartbeats.remove(endpoint)
            disconnectClient(endpoint)
            disconnectServer(endpoint)
        }
    }

    private fun l2capOutboundProgress(endpoint: String): Long =
        if (store.activeL2capSockets.containsKey(endpoint)) store.l2capOutboundProgressTimes[endpoint] ?: 0L else 0L

    /** A probe younger than the cap that is unsent or still inside its ack timeout. */
    private fun isProbePending(probe: HeartbeatChallenge, now: Long): Boolean =
        now - probe.createdAt < MAX_PROBE_AGE_MS && (probe.sentAt == 0L || !probe.expired(now, heartbeatAckTimeoutMs))

    private fun expireScans(activeEndpoints: Set<String>, now: Long) {
        store.endpointLastSeen.entries.toList().forEach { (endpoint, lastSeen) ->
            if (endpoint !in activeEndpoints && now - lastSeen > SCAN_EXPIRY_MS) {
                store.connectedEndpointIds.remove(endpoint)
                store.connectedEndpointNames.remove(endpoint)
                store.endpointLastSeen.remove(endpoint)
                store.endpointNodeIds.remove(endpoint)
                AppLogger.d("BLE_MESH", "Node Timed Out: $endpoint")
                onExpiredScan(endpoint)
            }
        }
    }

    fun start() {
        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }
    fun stop() = handler.removeCallbacks(runnable)

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val SCAN_EXPIRY_MS = 8_000L
        const val MAX_PROBE_AGE_MS = 30_000L

        /** A READY link with no admitted identity yet is left alone this long after READY. */
        const val FIRST_IDENTITY_GRACE_MS = 10_000L
    }
}
