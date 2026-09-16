package com.example.testresqmesh.feature.radar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.ui.state.RadarUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class RadarViewModel(private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()
    private val offlineSince = mutableMapOf<String, Long>()
    private val offlineRowLifetimeMs = 60_000L

    init {
        viewModelScope.launch {
            useCases.observeScannedDevices().collect { scanned ->
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
                _uiState.update { it.copy(knownNodes = known) }
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
