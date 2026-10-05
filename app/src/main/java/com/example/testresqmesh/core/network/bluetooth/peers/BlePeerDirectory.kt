package com.example.testresqmesh.core.network.bluetooth.peers

import android.bluetooth.*
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.BleStateStore

internal class BlePeerDirectory(
    private val store: BleStateStore,
    private val isBlocked: (String) -> Boolean
) {
    private fun isDeviceBlocked(deviceName: String): Boolean = isBlocked(deviceName)
    fun latestEndpointForIdentity(peerName: String, fallback: String): String {
        val targetId = NodeIdentity.idOf(peerName)
        return store.connectedEndpointNames.entries
            .asSequence()
            .filter { entry ->
                val name = entry.value
                NodeIdentity.matches(name, peerName) ||
                    (targetId != null && store.endpointNodeIds[entry.key] == targetId)
            }
            .maxByOrNull { store.endpointLastSeen[it.key] ?: Long.MIN_VALUE }
            ?.key ?: fallback
    }

    fun hasLiveSocket(endpointId: String): Boolean =
        store.activeConnections.containsKey(endpointId) || store.activeServerConnections.containsKey(endpointId)

    fun hasReadyEndpoint(endpointId: String): Boolean =
        hasLiveSocket(endpointId) && store.links.isReady(endpointId)

    fun hasReadyLinkToIdentity(peerName: String): Boolean {
        val targetId = NodeIdentity.idOf(peerName)
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        return endpoints.any { endpoint ->
            store.links.isReady(endpoint) &&
                (NodeIdentity.matches(store.connectedEndpointNames[endpoint], peerName) ||
                    (targetId != null && nodeIdForEndpoint(endpoint) == targetId))
        }
    }

    fun hasPayloadReadyDirectLink(): Boolean =
        (store.activeConnections.keys + store.activeServerConnections.keys).any { endpoint ->
            isUsableDirectNeighbor(endpoint)
        }

    fun isUsableDirectNeighbor(endpoint: String): Boolean {
        val name = store.connectedEndpointNames[endpoint]
        return com.example.testresqmesh.core.network.bluetooth.state.BleDirectPeerPolicy.isUsable(
            hasReadyEndpoint(endpoint), name, nodeIdForEndpoint(endpoint), name?.let(::isDeviceBlocked) == true
        )
    }

    fun linkEstablishedAt(endpointId: String): Long =
        store.connectionEstablishTime[endpointId] ?: Long.MAX_VALUE

    fun nodeIdForEndpoint(endpointId: String): String? =
        NodeIdentity.idOf(store.connectedEndpointNames[endpointId]) ?: store.endpointNodeIds[endpointId]

    fun findLinkEndpointByIdentity(peerName: String): String? {
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        endpoints.firstOrNull { NodeIdentity.matches(store.connectedEndpointNames[it], peerName) }
            ?.let { return it }

        // Fall back to the advertised node ID. This also covers provisional sockets whose name is
        // still a placeholder but whose ID was seeded from a previous scan.
        val targetId = NodeIdentity.idOf(peerName)
        if (targetId != null) {
            endpoints.firstOrNull { nodeIdForEndpoint(it) == targetId }?.let { return it }
        }
        return null
    }

    fun hasLinkToIdentity(peerName: String): Boolean = findLinkEndpointByIdentity(peerName) != null

    fun distinctLinkCount(): Int {
        val endpoints = store.activeConnections.keys + store.activeServerConnections.keys
        return endpoints
            .map { endpoint ->
                com.example.testresqmesh.core.network.bluetooth.state.BleDirectPeerPolicy.capacityKey(
                    store.connectedEndpointNames[endpoint], nodeIdForEndpoint(endpoint), endpoint
                )
            }
            .toSet()
            .size
    }

    fun distinctReadyLinkCount(): Int {
        val endpoints = (store.activeConnections.keys + store.activeServerConnections.keys)
            .filter { isUsableDirectNeighbor(it) }
        return endpoints
            .map { endpoint ->
                nodeIdForEndpoint(endpoint)
                    ?: NodeIdentity.key(store.connectedEndpointNames[endpoint]).ifEmpty { endpoint }
            }
            .toSet()
            .size
    }
}
