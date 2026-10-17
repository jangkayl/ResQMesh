package edu.cit.resqmesh.core.network.bluetooth.gatt.client

import android.bluetooth.*
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.model.ScanEvent
import edu.cit.resqmesh.core.network.bluetooth.gatt.GattClientHost
import edu.cit.resqmesh.core.network.bluetooth.gatt.client.notifyScanState

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
