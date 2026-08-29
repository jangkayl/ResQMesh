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

    private var cachedLocation: android.location.Location? = null
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null

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
    fun startLocationTracking(context: android.content.Context) {
        if (fusedLocationClient == null) {
            fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        }
        
        // Smart battery-efficient request: 30s interval, but only triggers if moved 20+ meters
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30000L)
            .setMinUpdateDistanceMeters(20f)
            .build()
            
        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { location ->
                    cachedLocation = location
                }
            }
        }
        
        try {
            fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback!!, android.os.Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission denied, ignore gracefully
        }
    }
    
    fun stopLocationTracking() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    @androidx.annotation.RequiresPermission(anyOf = ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"])
    fun sendEmergencySOS(context: android.content.Context, sosType: String) {
        val text = "🚨 CRITICAL SOS: $sosType EMERGENCY!"
        
        // 1. Instantly dispatch cached location with zero delay
        if (cachedLocation != null) {
            val msgId = repository.sendPublicMessage(text, null, null, cachedLocation!!.latitude, cachedLocation!!.longitude, isSOS = true)
            _activeSosMessageId.value = msgId
        } else {
            // Fallback: send without location instantly
            val msgId = repository.sendPublicMessage(text, null, null, null, null, isSOS = true)
            _activeSosMessageId.value = msgId
        }
        
        // 2. Start a background fetch for a high-accuracy pinpoint lock
        try {
            val client = fusedLocationClient ?: com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    // Check if the fresh location is significantly better/newer than cache
                    val isBetter = cachedLocation == null || location.accuracy < cachedLocation!!.accuracy || (location.time - cachedLocation!!.time > 60000)
                    if (isBetter) {
                        cachedLocation = location
                        // Send a follow-up pinpoint update!
                        repository.sendPublicMessage("📍 PINPOINT SOS UPDATE: More precise coordinates acquired.", null, null, location.latitude, location.longitude, isSOS = true)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if GPS fails, the rough cached location was already sent.
        }
    }

    fun cancelEmergencySOS() {
        _activeSosMessageId.value = null
        repository.clearSosAlert()
        repository.sendPublicMessage("✅ SOS Cancelled & Resolved", null, null, null, null, isSOS = false, isSOSCancel = true)
    }

    fun deleteConversationWith(peerName: String) {
        repository.deleteConversationWith(peerName)
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
