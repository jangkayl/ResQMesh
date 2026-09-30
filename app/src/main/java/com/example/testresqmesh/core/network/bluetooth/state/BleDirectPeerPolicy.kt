package com.example.testresqmesh.core.network.bluetooth.state

import com.example.testresqmesh.core.model.NodeIdentity

object BleDirectPeerPolicy {
    fun capacityKey(name: String?, nodeId: String?, endpoint: String): String =
        nodeId?.takeIf { it.isNotBlank() } ?: if (NodeIdentity.isPlaceholder(name)) endpoint else NodeIdentity.key(name)

    fun isUsable(ready: Boolean, name: String?, nodeId: String?, blocked: Boolean): Boolean =
        ready && !NodeIdentity.isPlaceholder(name) && !nodeId.isNullOrBlank() && !blocked
}
