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

import com.example.testresqmesh.data.repository.IdentityProvider

class SetupViewModel(
    private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases,
    private val identityProvider: IdentityProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _isDeveloperModeEnabled = MutableStateFlow(false)
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    private val _isLongRangeProfile = MutableStateFlow(true)
    val isLongRangeProfile: StateFlow<Boolean> = _isLongRangeProfile.asStateFlow()

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

    fun initDeveloperMode(context: Context) {
        val prefs = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
        _isDeveloperModeEnabled.value = prefs.getBoolean("developer_mode_enabled", false)
        _isLongRangeProfile.value = prefs.getBoolean("mesh_profile_long_range", true)
    }

    fun setMeshProfile(context: Context, longRange: Boolean) {
        context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("mesh_profile_long_range", longRange)
            .apply()
        _isLongRangeProfile.value = longRange
    }

    fun setDeveloperMode(context: Context, enabled: Boolean, pin: String? = null): Boolean {
        if (enabled) {
            if (pin != "0000") return false
            context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("developer_mode_enabled", true)
                .apply()
            _isDeveloperModeEnabled.value = true
            return true
        } else {
            context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("developer_mode_enabled", false)
                .apply()
            _isDeveloperModeEnabled.value = false
            return true
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
        val permanentNodeId = com.example.testresqmesh.core.network.CryptoManager.getMyNodeId()
        if (prefs.getString("node_id", null) != permanentNodeId) {
            prefs.edit().putString("node_id", permanentNodeId).apply()
        }
        return permanentNodeId
    }

    fun checkHardwareAndGoOnline(context: Context, customName: String, nodeTag: String, teamKey: String) {
        saveIdentity(context, customName, nodeTag)
        viewModelScope.launch {
            identityProvider.getOrCreateUser(customName)
        }
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
