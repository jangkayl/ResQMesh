package com.example.testresqmesh.core.ui.peers

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.model.RadarUiState
import com.example.testresqmesh.core.ui.model.NodeItemData
import com.example.testresqmesh.core.ui.model.NodeKind

/**
 * Derives the "Nearby Nodes" list from network state.
 *
 * The previous implementation decided a scanned device was reachable "Via Relay" by checking whether
 * its name appeared anywhere in [RadarUiState.topology]. That was never a valid test: every node that
 * emits a SYSTEM pulse becomes a topology key, including the peers we are *directly* connected to. So
 * whenever a live direct link was briefly missing from `connectedDevices` (a nameless inbound socket,
 * a dual-MAC rotation, or a stale discovery row) the device fell through to the scanned branch,
 * matched the topology, and was labelled "Connected (Via Relay)" despite being physically connected.
 *
 * Classification is now driven by explicit authoritative signals:
 *  - `connectedDevices`  -> a physical socket exists (DIRECT, or HANDSHAKING while still nameless).
 *  - `knownNodes.isDirect == false` -> genuinely only reachable through the mesh.
 *  - `scannedDevices`    -> in radio range but not routed yet.
 *
 * Kept as a pure function so it is cheap to reason about and unit-testable without Compose.
 */
internal fun classifyRadarNodes(state: RadarUiState): List<NodeItemData> {
    fun blockedNameFor(name: String): String? =
        state.blockedDeviceNames.firstOrNull { NodeIdentity.matches(it, name) }

    fun label(fullName: String): String {
        val display = NodeIdentity.displayNameOf(fullName).ifBlank { fullName }
        return display
    }

    // 1. Physical direct links. Authoritative: a socket either exists or it does not.
    val directNodes = state.connectedDevices
        .sortedByDescending { it.isPayloadReady }
        .distinctBy { NodeIdentity.key(it.name).ifEmpty { it.endpointId } }
        .map { device ->
            val handshaking = !device.isPayloadReady || device.isProvisional || NodeIdentity.isPlaceholder(device.name)
            if (handshaking) {
                NodeItemData(
                    endpointId = device.endpointId,
                    name = device.name,
                    label = "Node ${device.endpointId.takeLast(5)}",
                    status = "Linking (Handshaking)",
                    kind = NodeKind.HANDSHAKING,
                    isConnected = true,
                    isActiveRelay = false
                )
            } else if (!device.isPeerResponsive) {
                NodeItemData(
                    endpointId = device.endpointId,
                    name = device.name,
                    label = label(device.name),
                    status = "No response (checking connection)",
                    kind = NodeKind.UNRESPONSIVE,
                    isConnected = true,
                    isBlocked = blockedNameFor(device.name) != null
                )
            } else {
                NodeItemData(
                    endpointId = device.endpointId,
                    name = device.name,
                    label = label(device.name),
                    status = "ONLINE (Direct)",
                    kind = NodeKind.DIRECT,
                    isConnected = true,
                    isActiveRelay = true,
                    isBlocked = blockedNameFor(device.name) != null
                )
            }
        }

    fun isDirectPeer(name: String): Boolean =
        directNodes.any { NodeIdentity.matches(it.name, name) }

    // Node IDs of every peer we hold a socket to, including still-provisional links whose
    // placeholder name matches nothing. Without this a connected-but-unnamed peer kept its
    // discovery row and the Radar showed "Discovered / Scanning..." for a live connection.
    val linkedNodeIds = state.connectedDevices
        .mapNotNull { device -> device.nodeId.ifEmpty { NodeIdentity.idOf(device.name).orEmpty() }.ifEmpty { null } }
        .toSet()

    fun isLinkedById(nodeId: String, name: String): Boolean {
        val id = nodeId.ifEmpty { NodeIdentity.idOf(name).orEmpty() }
        return id.isNotEmpty() && linkedNodeIds.contains(id)
    }

    // 2. Nodes the router says are reachable only through the mesh.
    val indirectNodes = state.knownNodes
        .filter {
            !it.isDirect && !NodeIdentity.isPlaceholder(it.name) &&
                !isDirectPeer(it.name) && !isLinkedById("", it.name)
        }
        .distinctBy { NodeIdentity.key(it.name) }
        .map { node ->
            // Still advertising nearby -> it is a relay peer in radio range.
            // Not advertising    -> it is purely a mesh hop somewhere further out.
            val inRadioRange = state.scannedDevices.any { NodeIdentity.matches(it.name, node.name) }
            val blocked = blockedNameFor(node.name) != null
            NodeItemData(
                name = node.name,
                label = label(node.name),
                endpointId = state.scannedDevices.firstOrNull { NodeIdentity.matches(it.name, node.name) }?.endpointId.orEmpty(),
                status = if (inRadioRange) "Reachable nearby via relay" else "Reachable via mesh",
                kind = if (inRadioRange) NodeKind.RELAY else NodeKind.HOPPED,
                isConnected = false,
                isActiveRelay = true,
                isBlocked = blocked,
                route = node.route
            )
        }

    fun isRouted(name: String): Boolean =
        indirectNodes.any { NodeIdentity.matches(it.name, name) }

    // 3. Everything else we can physically see but have not linked or routed.
    val discoveredNodes = state.scannedDevices
        .filter {
            !isDirectPeer(it.name) && !isRouted(it.name) &&
                !NodeIdentity.isPlaceholder(it.name) && !isLinkedById(it.nodeId, it.name)
        }
        .distinctBy { NodeIdentity.key(it.name) }
        .map { scanned ->
            NodeItemData(
                endpointId = scanned.endpointId,
                name = scanned.name,
                label = label(scanned.name),
                status = if (scanned.isConnecting) "SYNCING..." else "Discovered / Scanning...",
                kind = if (scanned.isConnecting) NodeKind.SYNCING else NodeKind.DISCOVERED,
                isConnected = false,
                isActiveRelay = false,
                isBlocked = blockedNameFor(scanned.name) != null
            )
        }

    // 4. Keep a recently disconnected peer visible briefly so its status says OFFLINE.
    val offlineNodes = state.recentOfflineDevices
        .filter { device ->
            !isDirectPeer(device.name) && !isRouted(device.name) &&
                state.scannedDevices.none { NodeIdentity.matches(it.name, device.name) }
        }
        .distinctBy { NodeIdentity.key(it.name) }
        .map { device ->
            NodeItemData(
                endpointId = "",
                name = device.name,
                label = label(device.name),
                status = "OFFLINE",
                kind = NodeKind.OFFLINE,
                isBlocked = blockedNameFor(device.name) != null
            )
        }

    // 5. Blocked nodes that are entirely out of range, so the user can still unblock them.
    val visible = directNodes + indirectNodes + discoveredNodes + offlineNodes
    val offlineBlockedNodes = state.blockedDeviceNames
        .filter { blockedName -> visible.none { NodeIdentity.matches(it.name, blockedName) } }
        .map { name ->
            NodeItemData(
                endpointId = "",
                name = name,
                label = label(name),
                status = "OFFLINE",
                kind = NodeKind.BLOCKED_OFFLINE,
                isConnected = false,
                isActiveRelay = false,
                isBlocked = true
            )
        }

    return visible + offlineBlockedNodes
}
