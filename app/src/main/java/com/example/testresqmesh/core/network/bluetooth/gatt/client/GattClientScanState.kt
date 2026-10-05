package com.example.testresqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.bluetooth.gatt.GattClientHost
import com.example.testresqmesh.core.network.bluetooth.gatt.client.notifyScanState

internal fun GattClientHost.notifyScanState(macAddress: String, peerName: String, isConnecting: Boolean) {
    onDeviceScanned?.invoke(
        ScanEvent(
            endpointId = macAddress,
            name = peerName,
            nodeId = NodeIdentity.idOf(peerName) ?: store.endpointNodeIds[macAddress].orEmpty(),
            isConnecting = isConnecting
        )
    )
}
