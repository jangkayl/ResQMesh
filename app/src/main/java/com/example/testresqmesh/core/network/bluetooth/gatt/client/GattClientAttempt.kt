package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.os.Handler
import android.os.Looper
import com.example.testresqmesh.core.network.bluetooth.ReadyPayloadSetup
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost
import com.example.testresqmesh.core.network.bluetooth.gatt.client.notifyScanState

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

    val connectTimeoutRunnable = Runnable {
        if (!store.links.isCurrent(link)) return@Runnable
        AppLogger.d("BLE_MESH", "GATT Connection timed out after 15s. Forcing lock release for long-distance retry.")
        AppLogger.removeLink(macAddress, "CLIENT", link.generation)
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
