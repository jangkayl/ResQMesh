package com.example.testresqmesh.feature.setup.viewmodel

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
// Removed WifiManager import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.state.ConnectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.core.service.MeshSessionController
import com.example.testresqmesh.core.model.NodeIdentity

data class MeshStartError(val message: String, val needsAppPermission: Boolean = false)

class SetupViewModel(
    private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases,
    private val identityProvider: IdentityProvider,
    private val meshSessionController: MeshSessionController
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _isDeveloperModeEnabled = MutableStateFlow(false)
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    private val _isLongRangeProfile = MutableStateFlow(true)
    val isLongRangeProfile: StateFlow<Boolean> = _isLongRangeProfile.asStateFlow()
    val isBackgroundMeshEnabled: StateFlow<Boolean> = meshSessionController.backgroundMeshEnabled

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
            .putString("node_tag", NodeIdentity.optionalTag(tag))
            .apply()
    }

    fun getSavedName(context: Context): String {
        return context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
            .getString("custom_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun getSavedNodeId(context: Context): String {
        val prefs = context.getSharedPreferences("resqmesh_prefs", Context.MODE_PRIVATE)
        val permanentNodeId = com.example.testresqmesh.core.network.CryptoManager.getMyNodeId()
        if (prefs.getString("node_id", null) != permanentNodeId) {
            prefs.edit().putString("node_id", permanentNodeId).apply()
        }
        return permanentNodeId
    }

    /** Returns an actionable preflight error; a null result means startup was requested. */
    fun checkHardwareAndGoOnline(context: Context, customName: String, nodeTag: String, teamKey: String): MeshStartError? {
        saveIdentity(context, customName, nodeTag)
        viewModelScope.launch {
            identityProvider.getOrCreateUser(customName)
        }
        fun hasPermission(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !(hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE))
        ) {
            return startPreflightError("Allow Nearby Devices access in app settings, then try again.", needsAppPermission = true)
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return startPreflightError("Allow Location access in app settings, then try again.", needsAppPermission = true)
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val missing = mutableListOf<String>()
        val bluetoothEnabled = try {
            bluetoothAdapter?.isEnabled == true
        } catch (_: SecurityException) {
            return startPreflightError("Allow Nearby Devices access in app settings, then try again.", needsAppPermission = true)
        }
        val locationEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            return startPreflightError("Allow Location access in app settings, then try again.", needsAppPermission = true)
        }
        if (!bluetoothEnabled) missing.add("Bluetooth")
        if (!locationEnabled) missing.add("Location")

        if (missing.isNotEmpty()) {
            return startPreflightError("Turn on ${missing.joinToString(" and ")} in phone settings, then try again.")
        } else {
            val nodeId = getSavedNodeId(context)
            goOnline(customName, nodeTag, teamKey, nodeId)
            return null
        }
    }

    private fun startPreflightError(message: String, needsAppPermission: Boolean = false): MeshStartError {
        _uiState.update { it.copy(connectionStatus = message, isOnline = false) }
        return MeshStartError(message, needsAppPermission)
    }

    private fun goOnline(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        val myNodeName = NodeIdentity.qualifiedName(customName, nodeTag, nodeId)
        _uiState.update { it.copy(myNodeName = myNodeName) }
        meshSessionController.startNode(customName, nodeTag, teamKey, nodeId)
    }

    fun goOffline() {
        meshSessionController.stopNode()
    }

    fun setBackgroundMeshEnabled(enabled: Boolean) {
        meshSessionController.setBackgroundMeshEnabled(enabled)
    }
}
