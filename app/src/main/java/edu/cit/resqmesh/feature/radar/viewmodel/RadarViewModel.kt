package edu.cit.resqmesh.feature.radar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cit.resqmesh.core.model.ConnectedDevice
import edu.cit.resqmesh.core.model.KnownNode
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.model.RadarUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import edu.cit.resqmesh.core.utils.SCAN_UI_THROTTLE_MS
import edu.cit.resqmesh.core.utils.throttleLatest

class RadarViewModel(private val useCases: edu.cit.resqmesh.core.domain.usecase.MeshUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()
    private val offlineSince = mutableMapOf<String, Long>()
    private val offlineRowLifetimeMs = 60_000L

    init {
        viewModelScope.launch {
            useCases.observeScannedDevices().throttleLatest(SCAN_UI_THROTTLE_MS).collect { scanned ->
                _uiState.update { it.copy(scannedDevices = scanned) }
            }
        }
        viewModelScope.launch {
            useCases.observeConnectedDevices().collect { connected ->
                val previous = _uiState.value
                val now = System.currentTimeMillis()
                val newlyOffline = previous.connectedDevices.filter { old ->
                    old.isPayloadReady && !NodeIdentity.isPlaceholder(old.name) &&
                        connected.none { NodeIdentity.matches(it.name, old.name) }
                }
                newlyOffline.forEach { offlineSince[NodeIdentity.key(it.name)] = now }
                val retained = (previous.recentOfflineDevices + newlyOffline)
                    .distinctBy { NodeIdentity.key(it.name) }
                    .filter { device ->
                        val key = NodeIdentity.key(device.name)
                        connected.none { NodeIdentity.matches(it.name, device.name) } &&
                            now - (offlineSince[key] ?: now) < offlineRowLifetimeMs
                    }
                connected.forEach { offlineSince.remove(NodeIdentity.key(it.name)) }
                _uiState.update { it.copy(connectedDevices = connected, recentOfflineDevices = retained) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                _uiState.update { state ->
                    val retained = state.recentOfflineDevices.filter { device ->
                        now - (offlineSince[NodeIdentity.key(device.name)] ?: now) < offlineRowLifetimeMs
                    }
                    if (retained.size == state.recentOfflineDevices.size) state
                    else state.copy(recentOfflineDevices = retained)
                }
            }
        }
        viewModelScope.launch {
            useCases.observeBlockedDeviceNames().collect { blocked ->
                _uiState.update { it.copy(blockedDeviceNames = blocked) }
            }
        }
        viewModelScope.launch {
            useCases.observeKnownNodes().collect { known ->
                val previous = _uiState.value
                val now = System.currentTimeMillis()
                val retained = recentOfflineAfterRouteChange(
                    previous.recentOfflineDevices, previous.knownNodes, known, previous.connectedDevices
                )
                known.forEach { offlineSince.remove(NodeIdentity.key(it.name)) }
                retained.forEach { offlineSince.getOrPut(NodeIdentity.key(it.name)) { now } }
                _uiState.update { it.copy(knownNodes = known, recentOfflineDevices = retained) }
            }
        }
        viewModelScope.launch {
            useCases.observeTopology().collect { top ->
                _uiState.update { it.copy(topology = top) }
            }
        }
    }

    fun rescan() {
        useCases.rescan()
    }

    fun disconnectDevice(endpointId: String) {
        useCases.disconnectDevice(endpointId)
    }

    fun blockDevice(deviceName: String) {
        useCases.blockDevice(deviceName)
    }

    fun unblockDevice(deviceName: String) {
        useCases.unblockDevice(deviceName)
    }

    fun forceConnect(endpointId: String, endpointName: String) {
        useCases.forceConnect(endpointId, endpointName)
    }
}

/**
 * A routed peer with no remaining route or link becomes recently offline, the same as a lost direct
 * peer. This means unreachable from this phone; the mesh sends no proof that the peer itself stopped.
 */
internal fun recentOfflineAfterRouteChange(
    recentOffline: List<ConnectedDevice>,
    previousKnown: List<KnownNode>,
    known: List<KnownNode>,
    connected: List<ConnectedDevice>
): List<ConnectedDevice> {
    val lostRouted = previousKnown
        .filter { node ->
            !node.isDirect &&
                known.none { NodeIdentity.matches(it.name, node.name) } &&
                connected.none { NodeIdentity.matches(it.name, node.name) }
        }
        .map { ConnectedDevice(endpointId = "", name = it.name) }
    return (recentOffline.filter { device -> known.none { NodeIdentity.matches(it.name, device.name) } } + lostRouted)
        .distinctBy { NodeIdentity.key(it.name) }
}
