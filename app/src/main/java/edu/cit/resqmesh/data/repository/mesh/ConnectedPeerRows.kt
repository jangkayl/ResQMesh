package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ConnectedDevice

/**
 * Row decisions for the connected-peer list, which holds one row per peer even while that peer
 * has two sockets. Pure so the rules can be tested without a radio.
 */
internal object ConnectedPeerRows {

    /**
     * [duplicate] lost to the row on [keptEndpoint]. The kept row adopts the resolved identity, and
     * the duplicate's own row goes: its socket renames only once, so a placeholder left here would
     * never be repaired.
     */
    fun mergeIntoKept(
        rows: List<ConnectedDevice>,
        keptEndpoint: String,
        duplicate: ConnectedDevice,
        keptIsReady: Boolean
    ): List<ConnectedDevice> = rows
        .filter { it.endpointId != duplicate.endpointId }
        .map { row ->
            if (row.endpointId != keptEndpoint) row
            else row.copy(
                name = duplicate.name,
                isProvisional = false,
                nodeId = duplicate.nodeId.ifEmpty { row.nodeId },
                isPayloadReady = keptIsReady
            )
        }

    /**
     * Removes the row for [endpointId] unless [keepRow] says its socket is still live. When the peer
     * survives on another socket, [survivorEndpoint] takes over the row so a working peer stays listed.
     */
    fun afterDisconnect(
        rows: List<ConnectedDevice>,
        endpointId: String,
        keepRow: Boolean,
        survivorEndpoint: String?
    ): List<ConnectedDevice> {
        val target = survivorEndpoint?.takeIf { survivor ->
            survivor != endpointId && rows.none { it.endpointId == survivor }
        }
        return when {
            keepRow -> rows
            target == null -> rows.filter { it.endpointId != endpointId }
            else -> rows.map { row ->
                if (row.endpointId != endpointId) row
                else row.copy(endpointId = target, isPayloadReady = true, isPeerResponsive = true)
            }
        }
    }
}
