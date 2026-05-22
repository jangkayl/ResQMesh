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
    }

    fun rescan() {
        repository.rescan()
    }
}
