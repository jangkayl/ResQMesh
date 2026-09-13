package com.example.testresqmesh.feature.setup.viewmodel

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
// Removed WifiManager import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.state.ConnectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCases.observeIsOnline().collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
        viewModelScope.launch {
            useCases.observeConnectionStatus().collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
    }

    fun saveIdentity(context: Context, name: String, tag: String) {
        context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_name", name)
            .putString("node_tag", tag)
            .apply()
    }

    fun getSavedName(context: Context): String {
        return context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .getString("custom_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun getSavedTag(context: Context): String {
        return context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .getString("node_tag", "NODE") ?: "NODE"
    }

    fun getSavedNodeId(context: Context): String {
        val prefs = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
        var nodeId = prefs.getString("node_id", null)
        if (nodeId == null) {
            nodeId = java.util.UUID.randomUUID().toString().substring(0, 4).uppercase()
            prefs.edit().putString("node_id", nodeId).apply()
        }
        return nodeId
    }

    fun checkHardwareAndGoOnline(context: Context, customName: String, nodeTag: String, teamKey: String) {
        saveIdentity(context, customName, nodeTag)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val missing = mutableListOf<String>()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) missing.add("Bluetooth")
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) missing.add("Location/GPS")

        if (missing.isNotEmpty()) {
            val errorMsg = "HARDWARE ERROR: Please turn on ${missing.joinToString(", ")} to deploy Mesh Node."
            _uiState.update { it.copy(connectionStatus = errorMsg, isOnline = false) }
        } else {
            val nodeId = getSavedNodeId(context)
            goOnline(customName, nodeTag, teamKey, nodeId)
        }
    }

    private fun goOnline(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        val myNodeName = "$customName [$nodeTag]#$nodeId"
        _uiState.update { it.copy(myNodeName = myNodeName) }
        useCases.startNode(customName, nodeTag, teamKey, nodeId)
    }

    fun goOffline() {
        useCases.stopNode()
    }
}
