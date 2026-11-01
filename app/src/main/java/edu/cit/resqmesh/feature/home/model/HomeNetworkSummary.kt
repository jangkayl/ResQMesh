package edu.cit.resqmesh.feature.home.model

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.model.RadarUiState

data class HomeNetworkSummary(
    val directPeers: Int,
    val relayedPeers: Int,
    val nearbyPeers: Int,
    val checkingPeers: Int
) {
    @StringRes
    fun labelResource(): Int = when {
        directPeers > 0 -> R.string.home_network_direct
        relayedPeers > 0 -> R.string.home_network_relayed
        nearbyPeers > 0 -> R.string.home_network_nearby
        checkingPeers > 0 -> R.string.home_network_checking
        else -> R.string.home_network_empty
    }

    @Composable
    fun label(): String = stringResource(labelResource(), primaryCount())

    private fun primaryCount(): Int = when {
        directPeers > 0 -> directPeers
        relayedPeers > 0 -> relayedPeers
        nearbyPeers > 0 -> nearbyPeers
        else -> checkingPeers
    }
}

private fun isNamedReadyPeer(device: ConnectedDevice): Boolean =
    device.isPayloadReady && !device.isProvisional && !NodeIdentity.isPlaceholder(device.name)

private fun isActiveDirect(device: ConnectedDevice): Boolean = isNamedReadyPeer(device) && device.isPeerResponsive

private fun isChecking(device: ConnectedDevice): Boolean = isNamedReadyPeer(device) && !device.isPeerResponsive

// A blocked peer is never reachable for a direct link, so it counts in none of these totals.
private fun RadarUiState.isBlocked(name: String): Boolean = blockedDeviceNames.any { NodeIdentity.matches(it, name) }

internal fun homeNetworkSummary(state: RadarUiState): HomeNetworkSummary {
    val directNames = state.connectedDevices.filter(::isActiveDirect)
        .filterNot { state.isBlocked(it.name) }.map { it.name }
    val checkingNames = state.connectedDevices.filter(::isChecking)
        .filterNot { state.isBlocked(it.name) }.map { it.name }
    val relayedNames = state.knownNodes
        .filter { !it.isDirect && !NodeIdentity.isPlaceholder(it.name) && !state.isBlocked(it.name) }
        .map { it.name }

    val nearby = state.scannedDevices.count { scanned ->
        !NodeIdentity.isPlaceholder(scanned.name) && !state.isBlocked(scanned.name) &&
            directNames.none { NodeIdentity.matches(it, scanned.name) } &&
            checkingNames.none { NodeIdentity.matches(it, scanned.name) } &&
            relayedNames.none { NodeIdentity.matches(it, scanned.name) }
    }

    return HomeNetworkSummary(
        directPeers = directNames.distinctBy(NodeIdentity::key).size,
        relayedPeers = relayedNames.distinctBy(NodeIdentity::key).size,
        nearbyPeers = nearby,
        checkingPeers = checkingNames.distinctBy(NodeIdentity::key).size
    )
}
