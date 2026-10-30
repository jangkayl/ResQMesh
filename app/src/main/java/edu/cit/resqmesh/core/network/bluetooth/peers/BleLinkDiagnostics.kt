package edu.cit.resqmesh.core.network.bluetooth.peers

import edu.cit.resqmesh.core.network.LinkDiagnostic
import edu.cit.resqmesh.core.network.bluetooth.L2capTransport
import edu.cit.resqmesh.core.network.bluetooth.state.BleStateStore

/**
 * Read-only view of [BleStateStore.links] for the developer console. It only reads: the registry
 * snapshot is taken under its own lock, and the name and node ID maps are concurrent.
 */
internal object BleLinkDiagnostics {
    fun read(store: BleStateStore, directory: BlePeerDirectory, l2cap: L2capTransport): List<LinkDiagnostic> =
        store.links.snapshot().map { link ->
            LinkDiagnostic(
                endpoint = link.endpoint,
                role = link.role.name,
                generation = link.generation,
                state = link.state.name,
                nodeId = directory.nodeIdForEndpoint(link.endpoint) ?: link.peerNodeId,
                name = store.connectedEndpointNames[link.endpoint],
                identityAdmitted = link.identityAdmitted,
                transport = if (l2cap.hasWriter(link.endpoint)) "GATT+L2CAP" else "GATT",
                readyAt = link.readyAt
            )
        }
}
