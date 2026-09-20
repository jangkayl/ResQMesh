package com.example.testresqmesh.core.network.bluetooth

import android.os.Handler
import com.example.testresqmesh.core.network.bluetooth.state.BleLivenessPolicy
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore
import com.example.testresqmesh.core.network.bluetooth.state.HeartbeatCoordinator
import com.example.testresqmesh.core.utils.AppLogger

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
            if (!store.isNodeActive.get()) return
            val now = System.currentTimeMillis()
            val lockAge = now - store.connectLockAcquiredAt
            if (store.isConnecting.get() && store.connectLockAcquiredAt > 0L && lockAge > connectLockMaxHoldMs) {
                AppLogger.d("BLE_MESH", "Connect lock stuck for ${lockAge}ms on ${store.connectingMacAddress}. Force-releasing.")
                releaseConnectLock()
            }

            val activeEndpoints = (store.activeConnections.keys + store.activeServerConnections.keys).toSet()
            activeEndpoints.forEach { endpoint ->
                val lastInbound = store.connectionInteractionTimes[endpoint]
                    ?: store.connectionEstablishTime.computeIfAbsent(endpoint) { now }
                onLivenessChanged(endpoint, !BleLivenessPolicy.isUnresponsive(lastInbound, now))
                if (!store.activeL2capSockets.containsKey(endpoint) &&
                    now - lastInbound >= BleLivenessPolicy.UNRESPONSIVE_AFTER_MS && !heartbeats.contains(endpoint)) {
                    startHeartbeat(endpoint)
                }
                if (BleLivenessPolicy.isStale(lastInbound, now)) {
                    val probe = heartbeats.pending(endpoint)
                    if (probe != null && now - probe.createdAt < MAX_PROBE_AGE_MS &&
                        (probe.sentAt == 0L || !probe.expired(now, heartbeatAckTimeoutMs))) {
                        AppLogger.d("BLE_MESH", "Waiting for generation-bound GATT heartbeat result on $endpoint")
                    } else {
                        AppLogger.d("BLE_MESH", "No inbound progress from $endpoint for ${now - lastInbound}ms. Retiring stale GATT roles.")
                        heartbeats.remove(endpoint)
                        disconnectClient(endpoint)
                        disconnectServer(endpoint)
                    }
                } else {
                    store.endpointLastSeen[endpoint] = now
                }
            }

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
            onPulse()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() = handler.post(runnable)
    fun stop() = handler.removeCallbacks(runnable)

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val SCAN_EXPIRY_MS = 8_000L
        const val MAX_PROBE_AGE_MS = 30_000L
    }
}
