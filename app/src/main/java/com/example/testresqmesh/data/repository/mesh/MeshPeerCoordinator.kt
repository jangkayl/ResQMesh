package com.example.testresqmesh.data.repository.mesh

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.MeshTransportState
import com.example.testresqmesh.core.model.ScanEvent
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import com.example.testresqmesh.data.repository.MeshReadyPeerEvents
import com.example.testresqmesh.data.repository.MeshRouter
import com.example.testresqmesh.data.repository.PeerPublicKeyDirectory

internal class MeshPeerCoordinator(
    private val networkManager: MeshNetworkGateway,
    private val publicKeys: PeerPublicKeyDirectory,
    private val readyPeerEvents: MeshReadyPeerEvents,
    private val meshRouter: MeshRouter,
    private val _connectionStatus: MutableStateFlow<String>,
    private val _transportState: MutableStateFlow<MeshTransportState>,
    private val _connectedDevices: MutableStateFlow<List<ConnectedDevice>>,
    private val _scannedDevices: MutableStateFlow<List<ScannedDevice>>,
    private val recentPeerNames: MutableStateFlow<Map<String, String>>,
    private val peerNames: StateFlow<Map<String, String>>,
    private val observedPeerNames: Channel<String>,
    private val localName: () -> String,
    private val wakeOutbox: () -> Unit
) {
    private val myNodeName get() = localName()
    private fun scheduleOutboxFlush() = wakeOutbox()



    fun readyConnectedDevices(): List<ConnectedDevice> = _connectedDevices.value.filter {
        it.isPayloadReady && !NodeIdentity.isPlaceholder(it.name)
    }

    fun recordPeerName(name: String, fromReadyLink: Boolean = false) {
        val id = NodeIdentity.idOf(name) ?: return
        if (id == NodeIdentity.idOf(myNodeName)) return
        if (NodeIdentity.isPlaceholder(name) || name.substringBeforeLast('#').isBlank()) return
        if (!fromReadyLink && readyConnectedDevices().any { NodeIdentity.idOf(it.name) == id && it.name != name }) return
        if (recentPeerNames.value[id] == name) return
        recentPeerNames.update { it + (id to name) }
        observedPeerNames.trySend(name)
    }

    fun currentPeerName(name: String): String {
        val id = NodeIdentity.idOf(name) ?: return name
        return recentPeerNames.value[id]
            ?: peerNames.value[id]
            ?: readyConnectedDevices().firstOrNull { NodeIdentity.idOf(it.name) == id }?.name
            ?: name
    }

    private fun sameNode(a: ConnectedDevice, b: ConnectedDevice): Boolean {
        if (a.nodeId.isNotEmpty() && b.nodeId.isNotEmpty()) return a.nodeId == b.nodeId
        return NodeIdentity.matches(a.name, b.name)
    }

    fun onNetworkStatusChanged(status: String): Unit = run {
        _connectionStatus.value = status
    }

    fun onNetworkTransportStateChanged(state: MeshTransportState): Unit = run {
        _transportState.value = state
        if (state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.OFFLINE ||
            state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.BLUETOOTH_OFF ||
            state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.PERMISSION_REQUIRED ||
            state == com.example.testresqmesh.core.network.bluetooth.MeshTransportState.ERROR) {
            _connectedDevices.value = emptyList()
            _scannedDevices.value = emptyList()
            readyPeerEvents.update(emptyList())
            meshRouter.recalculateKnownNodes(myNodeName, emptyList())
        }
    }

    fun onNetworkDeviceConnected(device: ConnectedDevice): Unit = run {
        val existingById = _connectedDevices.value.find { it.endpointId == device.endpointId }
        val existingByIdentity = _connectedDevices.value.find {
            it.endpointId != device.endpointId && sameNode(it, device)
        }
        val existingIsReady = existingByIdentity?.let { networkManager.hasReadyEndpoint(it.endpointId) } ?: false
        if (existingById == null && !NodeIdentity.isPlaceholder(device.name) &&
            (existingByIdentity == null || !networkManager.hasLiveSocket(existingByIdentity.endpointId))) {
        }

        var updatedList = _connectedDevices.value
        var duplicateRejected = false

        // DUPLICATE ENDPOINT RESOLUTION.
        // This used to unconditionally disconnect the older endpoint the moment an identity
        // match appeared, which meant the app tore down its own healthy socket every time
        // dual-MAC produced a second entry for one peer: connect -> "ghost socket" ->
        // self-disconnect -> rescan -> reconnect. Now a stale row is merged away without
        // touching the radio, and when two sockets really are live the survivor is chosen
        // deterministically by age so the churn cannot keep winning.
        if (existingByIdentity != null && !NodeIdentity.isPlaceholder(device.name)) {
            val existingIsLive = networkManager.hasLiveSocket(existingByIdentity.endpointId)
            val incomingIsLive = networkManager.hasLiveSocket(device.endpointId)
            val incomingIsReady = networkManager.hasReadyEndpoint(device.endpointId)
            val preferredEndpoint = NodeIdentity.idOf(device.name)?.let(networkManager::preferredEndpointForPeer)

            when {
                !existingIsLive -> {
                    AppLogger.d("BLE_MESH", "Merging stale endpoint ${existingByIdentity.endpointId} into ${device.endpointId} for ${device.name}")
                    updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                    meshRouter.removeNode(existingByIdentity.name)
                }
                !incomingIsLive -> {
                    AppLogger.d("BLE_MESH", "Ignoring dead duplicate endpoint ${device.endpointId} for ${device.name}")
                    duplicateRejected = true
                }
                existingIsReady && !incomingIsReady -> {
                    AppLogger.d("BLE_MESH", "Keeping READY endpoint ${existingByIdentity.endpointId} for ${device.name}; alternate endpoint is still configuring")
                    duplicateRejected = true
                }
                incomingIsReady && !existingIsReady -> {
                    AppLogger.d("BLE_MESH", "Selecting READY endpoint ${device.endpointId} for ${device.name}; retaining alternate radio role")
                    updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                }
                preferredEndpoint == device.endpointId && preferredEndpoint != existingByIdentity.endpointId -> {
                    updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                }
                preferredEndpoint == existingByIdentity.endpointId -> duplicateRejected = true
                networkManager.linkEstablishedAt(existingByIdentity.endpointId) <= networkManager.linkEstablishedAt(device.endpointId) -> {
                    AppLogger.d("BLE_MESH", "Duplicate link to ${device.name}. Keeping older endpoint ${existingByIdentity.endpointId} in routing view.")
                    duplicateRejected = true
                }
                else -> {
                    AppLogger.d("BLE_MESH", "Duplicate link to ${device.name}. Keeping newer endpoint ${device.endpointId} in routing view.")
                    updatedList = updatedList.filter { it.endpointId != existingByIdentity.endpointId }
                }
            }
        }

        updatedList = when {
            // The surviving entry still adopts the freshly resolved name and identity.
            duplicateRejected && existingByIdentity != null -> updatedList.map {
                if (it.endpointId == existingByIdentity.endpointId) {
                    it.copy(
                        name = device.name,
                        isProvisional = false,
                        nodeId = device.nodeId.ifEmpty { it.nodeId },
                        isPayloadReady = existingIsReady
                    )
                } else it
            }
            duplicateRejected -> updatedList
            existingById != null -> {
                if (existingById.name != device.name) {
                    meshRouter.removeNode(existingById.name)
                }
                updatedList.map { if (it.endpointId == device.endpointId) device else it }
            }
            else -> updatedList + device
        }

        // Always purge the discovery list for this peer, including on the rename path. Previously
        // only brand new devices purged it, so a peer first seen as a nameless inbound socket left
        // a stale scanned row behind on its advertising MAC. That row is what the Radar rendered
        // as "Connected (Via Relay)" for an already directly connected device.
        _scannedDevices.value = _scannedDevices.value.filter {
            it.endpointId != device.endpointId &&
                !NodeIdentity.matches(it.name, device.name) &&
                !(device.nodeId.isNotEmpty() && it.nodeId == device.nodeId)
        }

        // Provisional links have a real socket but only a placeholder name, so they must not be
        // published into the routing tables or they would pollute the topology with ghost nodes.
        if (device.isPayloadReady && !device.isProvisional && !NodeIdentity.isPlaceholder(device.name)) {
            meshRouter.markNodeSeen(device.name)
            recordPeerName(device.name, fromReadyLink = true)
        }

        _connectedDevices.value = updatedList
        readyPeerEvents.update(updatedList.filterNot { networkManager.isDeviceBlocked(it.name) })
        meshRouter.recalculateKnownNodes(myNodeName, updatedList.filter { it.isPayloadReady })
        if (device.isPayloadReady) {
            scheduleOutboxFlush()
            networkManager.wakePrivateReceipts()
            readyPeerEvents.publish(device)
        }
    }

    fun onNetworkDeviceDisconnected(endpointId: String): Unit = run {
        val disconnectedDevice = _connectedDevices.value.find { it.endpointId == endpointId }
        val peerStillReady = disconnectedDevice != null && !NodeIdentity.isPlaceholder(disconnectedDevice.name) &&
            networkManager.hasReadyLinkToIdentity(disconnectedDevice.name)
        if (disconnectedDevice != null && !NodeIdentity.isPlaceholder(disconnectedDevice.name) && !peerStillReady) {
        }
        _connectedDevices.value = _connectedDevices.value.filter {
            it.endpointId != endpointId || peerStillReady && networkManager.hasLiveSocket(endpointId)
        }
        if (disconnectedDevice != null && !peerStillReady) {
            meshRouter.onDirectPeerLost(myNodeName, disconnectedDevice.name, readyConnectedDevices())
        }
        meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
        readyPeerEvents.update(readyConnectedDevices().filterNot { networkManager.isDeviceBlocked(it.name) })
    }

    fun onNetworkDeviceLivenessChanged(endpointId: String, responsive: Boolean): Unit = run {
        val current = _connectedDevices.value
        if (current.any { it.endpointId == endpointId && it.isPeerResponsive != responsive }) {
            _connectedDevices.value = current.map { device ->
                if (device.endpointId == endpointId) device.copy(isPeerResponsive = responsive) else device
            }
        }
    }

    fun onNetworkPublicKeyReceived(senderName: String, senderNodeId: String, key: String): Unit = run {
        when (publicKeys.observe(senderName, senderNodeId, key)) {
            PeerPublicKeyDirectory.Observation.KEY_CHANGE_PENDING ->
                AppLogger.d("BLE_MESH", "Public-key change requires approval for $senderName")
            PeerPublicKeyDirectory.Observation.INVALID_IDENTITY ->
                AppLogger.d("BLE_MESH", "Ignored public key with invalid stable identity")
            else -> {
                AppLogger.d("BLE_MESH", "Recorded public-key observation for $senderName")
                scheduleOutboxFlush()
            }
        }
    }

    fun onNetworkRoutingTableReceived(senderName: String, senderNodeId: String, connectedNodes: List<String>, connectedNodeIds: List<String>, topologySequence: Long): Unit = run {
        val applied = meshRouter.updateTopology(senderName, senderNodeId, connectedNodes, connectedNodeIds, myNodeName, topologySequence)
        if (applied) {
            meshRouter.recalculateKnownNodes(myNodeName, readyConnectedDevices())
            scheduleOutboxFlush()
            AppLogger.event(
                category = com.example.testresqmesh.core.utils.TerminalLogCategory.ROUTING,
                event = "TOPOLOGY_UPDATED",
                message = "Applied topology version $topologySequence from peer; ${connectedNodes.size} advertised neighbor(s)",
                peerName = senderName
            )
        }
    }

    fun networkRouteExists(targetName: String): Boolean = run {
        meshRouter.findShortestPath(myNodeName, targetName, readyConnectedDevices()).isNotEmpty()
    }

    fun canNetworkRetireForBridge(endpoint: String): Boolean = run {
        meshRouter.canRetireForBridge(myNodeName, endpoint, readyConnectedDevices().filter {
            it.isPeerResponsive && !networkManager.isDeviceBlocked(it.name) && networkManager.hasReadyEndpoint(it.endpointId)
        })
    }

    fun onNetworkDeviceScanned(event: ScanEvent): Unit = run {
        if (!NodeIdentity.matches(event.name, myNodeName)) {
            // A peer we already hold a physical socket to must never appear in the discovery list.
            // Node ID is checked too, so a still-provisional link (placeholder name, matches
            // nothing by name) also suppresses its own advertising MAC.
            val isPhysicallyConnected = _connectedDevices.value.any {
                it.endpointId == event.endpointId ||
                    NodeIdentity.matches(it.name, event.name) ||
                    (event.nodeId.isNotEmpty() && it.nodeId == event.nodeId)
            }

            if (isPhysicallyConnected) {
                _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != event.endpointId }
            } else {
                val currentScanned = _scannedDevices.value.toMutableList()
                val existingIndex = currentScanned.indexOfFirst {
                    it.endpointId == event.endpointId || NodeIdentity.matches(it.name, event.name)
                }

                if (existingIndex != -1) {
                    val existing = currentScanned[existingIndex]
                    currentScanned[existingIndex] = existing.copy(
                        endpointId = event.endpointId,
                        name = event.name.ifBlank { existing.name },
                        lastSeen = System.currentTimeMillis(),
                        powerScore = event.peerConnections ?: existing.powerScore,
                        myRole = event.peerScore ?: existing.myRole,
                        isConnecting = event.isConnecting,
                        nodeId = event.nodeId.ifEmpty { existing.nodeId }
                    )
                    _scannedDevices.value = currentScanned
                } else if (event.name.isNotBlank()) {
                    currentScanned.add(
                        ScannedDevice(
                            endpointId = event.endpointId,
                            name = event.name,
                            lastSeen = System.currentTimeMillis(),
                            powerScore = event.peerConnections ?: 0,
                            myRole = event.peerScore ?: "IDLE",
                            isConnecting = event.isConnecting,
                            nodeId = event.nodeId
                        )
                    )
                    _scannedDevices.value = currentScanned
                }
            }
        }
    }

    fun onNetworkDeviceScanRemoved(id: String): Unit = run {
        _scannedDevices.value = _scannedDevices.value.filter { it.endpointId != id }
    }

    fun networkSpanningTreeNeighbors(): Set<String> = run {
        meshRouter.getSpanningTreeNeighbors(myNodeName, readyConnectedDevices())
    }
}
