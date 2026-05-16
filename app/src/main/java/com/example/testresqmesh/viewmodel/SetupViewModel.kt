package com.example.testresqmesh.viewmodel

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.states.ConnectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(private val repository: MeshRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isOnline.collect { isOnline ->
                _uiState.update { it.copy(isOnline = isOnline) }
            }
        }
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
    }

    fun checkHardwareAndGoOnline(context: Context, customName: String, nodeTag: String, teamKey: String) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val missing = mutableListOf<String>()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) missing.add("Bluetooth")
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) missing.add("Location/GPS")
        if (!wifiManager.isWifiEnabled) missing.add("Wi-Fi")

        if (missing.isNotEmpty()) {
            val errorMsg = "HARDWARE ERROR: Please turn on ${missing.joinToString(", ")} to deploy Mesh Node."
            _uiState.update { it.copy(connectionStatus = errorMsg, isOnline = false) }
        } else {
            goOnline(customName, nodeTag, teamKey)
        }
    }

    private fun goOnline(customName: String, nodeTag: String, teamKey: String) {
        val myNodeName = "$customName [$nodeTag]"
        _uiState.update { it.copy(myNodeName = myNodeName) }
        repository.startNode(customName, nodeTag, teamKey)
    }

    fun goOffline() {
        repository.stopNode()
    }
}
