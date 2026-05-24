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
    
    private val _activeSosMessageId = MutableStateFlow<String?>(null)
    val activeSosMessageId: StateFlow<String?> = _activeSosMessageId.asStateFlow()
    
    val incomingSosAlert = repository.incomingSosAlert
    
    fun clearSosAlert() {
        repository.clearSosAlert()
    }

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
        viewModelScope.launch {
            repository.knownNodes.collect { nodes ->
                _uiState.update { it.copy(knownNodes = nodes) }
            }
        }
    }

    fun sendPublicMessage(text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPublicMessage(text, imageBase64, audioBase64)
    }

    @androidx.annotation.RequiresPermission(anyOf = ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"])
    fun sendEmergencySOS(context: android.content.Context, sosType: String) {
        val text = "🚨 CRITICAL SOS: $sosType EMERGENCY!"
        
        try {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    val msgId = repository.sendPublicMessage(text, null, null, location.latitude, location.longitude, isSOS = true)
                    _activeSosMessageId.value = msgId
                } else {
                    // Fallback to no location if null
                    val msgId = repository.sendPublicMessage(text, null, null, null, null, isSOS = true)
                    _activeSosMessageId.value = msgId
                }
            }.addOnFailureListener {
                // Fallback to no location on error
                val msgId = repository.sendPublicMessage(text, null, null, null, null, isSOS = true)
                _activeSosMessageId.value = msgId
            }
        } catch (e: Exception) {
            // Permission missing or service unavailable
            val msgId = repository.sendPublicMessage(text, null, null, null, null, isSOS = true)
            _activeSosMessageId.value = msgId
        }
    }

    fun cancelEmergencySOS() {
        _activeSosMessageId.value = null
        repository.clearSosAlert()
        repository.sendPublicMessage("✅ SOS Cancelled & Resolved", null, null, null, null, isSOS = false, isSOSCancel = true)
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPrivateMessage(targetName, text, imageBase64, audioBase64)
    }

    fun disconnectDevice(endpointId: String) {
        repository.disconnectDevice(endpointId)
    }

    fun markMessageAsSeen(messageId: String, isPrivate: Boolean, targetId: String? = null) {
        repository.broadcastSeenReceipt(messageId, isPrivate, targetId)
    }

    @androidx.annotation.RequiresPermission(anyOf = ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"])
    fun broadcastLocation(context: android.content.Context, isPrivate: Boolean, targetName: String? = null) {
        // The BEST way to get location on Android (Handles indoors via Wi-Fi/Cell + outdoors via GPS seamlessly)
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        
        try {
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    sendLocationMessage(location, isPrivate, targetName)
                } else {
                    // Fallback to cache if fresh fetch miraculously fails
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            sendLocationMessage(lastLoc, isPrivate, targetName)
                        } else {
                            sendLocationError(isPrivate, targetName)
                        }
                    }.addOnFailureListener { sendLocationError(isPrivate, targetName) }
                }
            }.addOnFailureListener {
                sendLocationError(isPrivate, targetName)
            }
        } catch (e: SecurityException) {
            sendLocationError(isPrivate, targetName)
        }
    }

    private fun sendLocationMessage(location: android.location.Location, isPrivate: Boolean, targetName: String?) {
        if (isPrivate && targetName != null) {
            repository.sendPrivateMessage(targetName, "📍 I am sharing my location.", null, null, location.latitude, location.longitude)
        } else {
            repository.sendPublicMessage("📍 I am sharing my location.", null, null, location.latitude, location.longitude)
        }
    }

    private fun sendLocationError(isPrivate: Boolean, targetName: String?) {
        val errorMsg = "⚠️ Failed to get fresh GPS lock. Make sure Location is on, and you have sky visibility."
        if (isPrivate && targetName != null) {
            repository.sendPrivateMessage(targetName, errorMsg, null, null, null, null)
        } else {
            repository.sendPublicMessage(errorMsg, null, null, null, null)
        }
    }
}
