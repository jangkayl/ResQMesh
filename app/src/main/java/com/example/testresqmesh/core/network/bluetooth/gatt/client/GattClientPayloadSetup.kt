package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import android.os.Build
import com.example.testresqmesh.core.network.bluetooth.ReadyPayloadSetup
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import com.example.testresqmesh.core.network.bluetooth.state.BleLinkState
import com.example.testresqmesh.core.utils.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost

internal fun createGattClientPayloadSetup(
    manager: GattClientHost,
    epoch: Long,
    link: BleLink,
    macAddress: String,
    readPort: (BluetoothGattCharacteristic) -> Boolean,
    requestMtu: () -> Boolean
): ReadyPayloadSetup = with(manager) {
    ReadyPayloadSetup(
        isCurrentReady = { isTransportGenerationCurrent(epoch) && store.links.isCurrent(link) && link.state == BleLinkState.READY },
        isGattIdle = { !store.gattFlights.containsKey(macAddress) && !store.serverIndications.unresolved(macAddress) && link.currentOperation == null },
        hasL2cap = { store.activeL2capSockets[macAddress]?.isConnected == true },
        requestPort = {
            val characteristic = link.gatt?.getService(SERVICE_UUID)?.getCharacteristic(L2CAP_PSM_CHARACTERISTIC_UUID)
            characteristic != null && runCatching { readPort(characteristic) }.getOrDefault(false)
        },
        requestMtu = { runCatching { requestMtu() }.getOrDefault(false) },
        openPort = { port, done ->
            val socket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                runCatching { link.gatt?.device?.createInsecureL2capChannel(port) }.getOrNull() else null
            if (socket == null || !trackPendingL2capSocket(socket, epoch)) done(false)
            else {
                val finished = AtomicBoolean(false)
                handler.postDelayed({
                    if (finished.compareAndSet(false, true)) {
                        runCatching { socket.close() }; finishPendingL2capSocket(socket); done(false)
                    }
                }, 10_000L)
                Thread {
                    val connected = runCatching { socket.connect(); true }.getOrDefault(false)
                    handler.post {
                        finishPendingL2capSocket(socket)
                        if (finished.compareAndSet(false, true)) {
                            val owned = connected && isTransportGenerationCurrent(epoch) && store.links.isCurrent(link)
                            if (owned) handleL2capConnection(macAddress, socket) else runCatching { socket.close() }
                            done(owned)
                        } else runCatching { socket.close() }
                    }
                }.start()
            }
        },
        schedule = { delay, action -> handler.postDelayed({ action() }, delay) },
        gate = { operation ->
            if (store.links.isCurrent(link)) {
                link.currentOperation = operation
                if (operation == null) processNextPayload(macAddress)
            }
        },
        log = { AppLogger.d("BLE_MESH", "PAYLOAD_SETUP generation=${link.generation} endpoint=$macAddress $it") },
        supportsPort = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && com.example.testresqmesh.BuildConfig.BLE_L2CAP_ENABLED,
        onStalled = { link.gattQuarantined = true; forceGattDisconnect(macAddress, link.gatt) }
    )
}
