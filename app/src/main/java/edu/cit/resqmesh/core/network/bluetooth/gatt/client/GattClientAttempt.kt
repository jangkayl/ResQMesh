package edu.cit.resqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.os.Handler
import android.os.Looper
import edu.cit.resqmesh.core.network.bluetooth.ReadyPayloadSetup
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.BleLinkState
import edu.cit.resqmesh.core.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattClientHost
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.notifyScanState

internal class GattClientAttempt(
    val manager: GattClientHost,
    val macAddress: String,
    val peerName: String,
    val epoch: Long,
    val link: BleLink,
    val payloadSetup: ReadyPayloadSetup,
    val stablePeerId: String?,
    val useExplicitLeTransport: Boolean,
    val radioOwner: String,
    private val discoverServices: (BluetoothGatt) -> Boolean,
    private val retireTimedOutGatt: () -> Unit
) : GattClientHost by manager {
    val timeoutHandler = Handler(Looper.getMainLooper())
    // Service discovery is the only readiness-critical operation started after CONNECTED.
    // MTU negotiation used to run first, but some Samsung stacks begin their own cache/service
    // work at connection time. The MTU request then overlapped that work and the later discovery
    // fallback was rejected as "already has a pending command".
    val servicesRequested = AtomicBoolean(false)
    var discoveryRequestAttempts = 0

    fun requestServicesOnce(gatt: BluetoothGatt) {
        if (store.links.isCurrent(link) && servicesRequested.compareAndSet(false, true)) {
            discoveryRequestAttempts += 1
            link.currentOperation = "DISCOVER_SERVICES"
            val accepted = discoverServices(gatt)
            AppLogger.d("BLE_MESH", "Service discovery request for $peerName accepted=$accepted attempt=$discoveryRequestAttempts")
            if (!accepted) {
                link.currentOperation = null
                servicesRequested.set(false)
                if (discoveryRequestAttempts < MAX_DISCOVERY_REQUEST_ATTEMPTS) {
                    timeoutHandler.postDelayed({ requestServicesOnce(gatt) }, DISCOVERY_REQUEST_RETRY_MS)
                }
            }
        }
    }

    fun finishConnectPhase(reason: String) {
        timeoutHandler.removeCallbacksAndMessages(null)
        finishRadioHandshake(radioOwner, reason)
        if (!store.links.isCurrent(link)) return
        releaseConnectLock(macAddress, reason)
        if (store.connectingMacAddress == null) {
            handler.post { notifyScanState(macAddress, peerName, isConnecting = false) }
        }
    }

    /** Stable identity for the per-peer deadline; private addresses rotate between attempts. */
    val connectTimeoutKey: String get() = stablePeerId ?: peerName
    val connectTimeoutMs: Long = store.connectTimeouts.timeoutMs(connectTimeoutKey)

    val connectTimeoutRunnable = Runnable {
        if (!store.links.isCurrent(link)) return@Runnable
        store.connectTimeouts.onTimedOut(connectTimeoutKey)
        AppLogger.d(
            "BLE_MESH",
            "GATT connection to $peerName timed out after ${connectTimeoutMs}ms; " +
                "next deadline ${store.connectTimeouts.timeoutMs(connectTimeoutKey)}ms"
        )
        finishConnectPhase("connect timeout")
        store.links.transition(link, BleLinkState.DISCONNECTING)
        try { retireTimedOutGatt() } catch (e: Exception) {}
        store.links.forget(link)
    }

    fun owns(gatt: BluetoothGatt, event: String): Boolean {
        if (!isTransportGenerationCurrent(epoch) || !store.links.isCurrent(link) || (link.gatt != null && link.gatt !== gatt)) {
            AppLogger.d("BLE_MESH", "Ignoring $event from stale CLIENT link ${link.generation} on $macAddress")
            return false
        }
        if (link.gatt == null) link.gatt = gatt
        return true
    }
}
