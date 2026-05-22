package com.example.testresqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.state.ChatUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunicationViewModel(private val repository: MeshRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.publicMessages.collect { messages ->
                _uiState.update { it.copy(publicMessages = messages) }
            }
        }
        viewModelScope.launch {
            repository.privateMessages.collect { messagesMap ->
                _uiState.update { it.copy(privateMessages = messagesMap) }
            }
        }
        viewModelScope.launch {
            repository.connectedDevices.collect { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }
    }

    fun sendPublicMessage(text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPublicMessage(text, imageBase64, audioBase64)
    }

    fun sendPrivateMessage(target: ConnectedDevice, text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPrivateMessage(target, text, imageBase64, audioBase64)
    }

    fun markMessageAsSeen(messageId: String, isPrivate: Boolean, targetId: String? = null) {
        repository.broadcastSeenReceipt(messageId, isPrivate, targetId)
    }

    @androidx.annotation.RequiresPermission(anyOf = ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"])
    fun broadcastLocation(context: android.content.Context, isPrivate: Boolean, targetDevice: ConnectedDevice? = null) {
        // The BEST way to get location on Android (Handles indoors via Wi-Fi/Cell + outdoors via GPS seamlessly)
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        
        try {
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    sendLocationMessage(location, isPrivate, targetDevice)
                } else {
                    // Fallback to cache if fresh fetch miraculously fails
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            sendLocationMessage(lastLoc, isPrivate, targetDevice)
                        } else {
                            sendLocationError(isPrivate, targetDevice)
                        }
                    }.addOnFailureListener { sendLocationError(isPrivate, targetDevice) }
                }
            }.addOnFailureListener {
                sendLocationError(isPrivate, targetDevice)
            }
        } catch (e: SecurityException) {
            sendLocationError(isPrivate, targetDevice)
        }
    }

    private fun sendLocationMessage(location: android.location.Location, isPrivate: Boolean, targetDevice: ConnectedDevice?) {
        if (isPrivate && targetDevice != null) {
            repository.sendPrivateMessage(targetDevice, "📍 I am sharing my location.", null, null, location.latitude, location.longitude)
        } else {
            repository.sendPublicMessage("📍 I am sharing my location.", null, null, location.latitude, location.longitude)
        }
    }

    private fun sendLocationError(isPrivate: Boolean, targetDevice: ConnectedDevice?) {
        val errorMsg = "⚠️ Failed to get fresh GPS lock. Make sure Location is on, and you have sky visibility."
        if (isPrivate && targetDevice != null) {
            repository.sendPrivateMessage(targetDevice, errorMsg, null, null, null, null)
        } else {
            repository.sendPublicMessage(errorMsg, null, null, null, null)
        }
    }
}
