package com.example.testresqmesh.feature.radar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.state.RadarUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RadarViewModel(private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCases.observeScannedDevices().collect { scanned ->
                _uiState.update { it.copy(scannedDevices = scanned) }
            }
        }
        viewModelScope.launch {
            useCases.observeConnectedDevices().collect { connected ->
                _uiState.update { it.copy(connectedDevices = connected) }
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
