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

class RadarViewModel(private val repository: MeshRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.scannedDevices.collect { scanned ->
                _uiState.update { it.copy(scannedDevices = scanned) }
            }
        }
        viewModelScope.launch {
            repository.connectedDevices.collect { connected ->
                _uiState.update { it.copy(connectedDevices = connected) }
            }
        }
        viewModelScope.launch {
            repository.blockedDeviceNames.collect { blocked ->
                _uiState.update { it.copy(blockedDeviceNames = blocked) }
            }
        }
    }

    fun rescan() {
        repository.rescan()
    }

    fun disconnectDevice(endpointId: String) {
        repository.disconnectDevice(endpointId)
    }

    fun blockDevice(deviceName: String) {
        repository.blockDevice(deviceName)
    }

    fun unblockDevice(deviceName: String) {
        repository.unblockDevice(deviceName)
    }

    fun forceConnect(endpointId: String, endpointName: String) {
        repository.forceConnect(endpointId, endpointName)
    }
}
