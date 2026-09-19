package com.example.testresqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.state.ChatUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunicationViewModel(
    private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases,
    private val locationClient: com.example.testresqmesh.core.location.LocationClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _privateSendErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val privateSendErrors: SharedFlow<String> = _privateSendErrors
    private val _pendingKeyVerification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingKeyVerification: SharedFlow<String> = _pendingKeyVerification
    private val _privateDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val privateDrafts: StateFlow<Map<String, String>> = _privateDrafts.asStateFlow()
    
    private val _activeSosMessageId = MutableStateFlow<String?>(null)
    val activeSosMessageId: StateFlow<String?> = _activeSosMessageId.asStateFlow()
    
    val incomingSosAlert = useCases.observeIncomingSosAlert()
    
    fun clearSosAlert() {
        useCases.clearSosAlert()
    }
    
    val currentChannelId: StateFlow<String> = useCases.observeCurrentChannelId()
    
    fun setChannel(channelId: String) {
        useCases.setChannel(channelId)
    }

    fun rescan() {
        useCases.rescan()
    }

    init {
        viewModelScope.launch {
            useCases.observePublicMessages().collect { messages ->
                _uiState.update { it.copy(publicMessages = messages) }
            }
        }
        viewModelScope.launch {
            useCases.observePrivateMessages().collect { messagesMap ->
                _uiState.update { it.copy(privateMessages = messagesMap) }
            }
        }
        viewModelScope.launch {
            useCases.observeConnectedDevices().collect { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }

        }
        viewModelScope.launch {
            useCases.observeKnownNodes().collect { nodes ->
                _uiState.update { it.copy(knownNodes = nodes) }
            }
        }
        viewModelScope.launch {
            useCases.observeScannedDevices().collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
        viewModelScope.launch {
            useCases.observeBlockedDeviceNames().collect { blocked ->
                _uiState.update { it.copy(blockedDeviceNames = blocked) }
            }
        }
    }

    fun sendPublicMessage(text: String, imageBase64: String? = null, audioBase64: String? = null) {
        useCases.sendPublicMessage(text, imageBase64, audioBase64)
    }

    fun startLocationTracking() {
        locationClient.startTracking(30000L)
    }
    
    fun stopLocationTracking() {
        locationClient.stopTracking()
    }

    fun sendEmergencySOS(sosType: String) {
        val text = "🚨 CRITICAL SOS: $sosType EMERGENCY!"
        val cachedLocation = locationClient.getLastKnownLocation()
        
        // 1. Instantly dispatch cached location with zero delay
        if (cachedLocation != null) {
            val msgId = useCases.sendPublicMessage(text, null, null, cachedLocation.latitude, cachedLocation.longitude, isSOS = true)
            _activeSosMessageId.value = msgId
        } else {
            // Fallback: send without location instantly
            val msgId = useCases.sendPublicMessage(text, null, null, null, null, isSOS = true)
            _activeSosMessageId.value = msgId
        }
        
        // 2. Start a background fetch for a high-accuracy pinpoint lock
        locationClient.requestPinpointLocation { location ->
            if (location != null) {
                // Check if the fresh location is significantly better/newer than cache
                val isBetter = cachedLocation == null || location.accuracy < cachedLocation.accuracy || (location.time - cachedLocation.time > 60000)
                if (isBetter) {
                    // Send a follow-up pinpoint update!
                    useCases.sendPublicMessage("📍 PINPOINT SOS UPDATE: More precise coordinates acquired.", null, null, location.latitude, location.longitude, isSOS = true)
                }
            }
        }
    }

    fun cancelEmergencySOS() {
        _activeSosMessageId.value = null
        useCases.clearSosAlert()
        useCases.sendPublicMessage("✅ SOS Cancelled & Resolved", null, null, null, null, isSOS = false, isSOSCancel = true)
    }

    fun deleteConversationWith(peerName: String) {
        useCases.deleteConversationWith(peerName)
    }

    fun sendPrivateMessage(targetName: String, text: String, imageBase64: String? = null, audioBase64: String? = null): Boolean {
        val sent = useCases.sendPrivateMessage(targetName, text, imageBase64, audioBase64)
        if (!sent) {
            if (useCases.hasPendingPublicKeyChange(targetName)) _pendingKeyVerification.tryEmit(targetName)
            else reportPrivateSendFailure()
        }
        return sent
    }

    fun acceptPendingPublicKeyChange(targetName: String): Boolean = useCases.acceptPendingPublicKeyChange(targetName)

    private fun reportPrivateSendFailure() {
        _privateSendErrors.tryEmit("Private message not sent. Waiting for a ready connection and current recipient key.")
    }

    fun updatePrivateDraft(targetName: String, draft: String) {
        _privateDrafts.update { drafts ->
            if (draft.isBlank()) drafts - targetName else drafts + (targetName to draft)
        }
    }

    fun clearPrivateDraft(targetName: String) {
        _privateDrafts.update { it - targetName }
    }

    fun disconnectDevice(endpointId: String) {
        useCases.disconnectDevice(endpointId)
    }

    fun markMessageAsSeen(messageId: String, isPrivate: Boolean, targetId: String? = null) {
        useCases.broadcastSeenReceipt(messageId, isPrivate, targetId)
    }

    @androidx.annotation.RequiresPermission(anyOf = ["android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"])
    fun broadcastLocation(context: android.content.Context, isPrivate: Boolean, targetName: String? = null) {
        // The BEST way to get location on Android (Handles indoors via Wi-Fi/Cell + outdoors via GPS seamlessly)
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context.applicationContext)
        
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
            if (!useCases.sendPrivateMessage(targetName, "📍 I am sharing my location.", null, null, location.latitude, location.longitude)) {
                reportPrivateSendFailure()
            }
        } else {
            useCases.sendPublicMessage("📍 I am sharing my location.", null, null, location.latitude, location.longitude)
        }
    }

    private fun sendLocationError(isPrivate: Boolean, targetName: String?) {
        val errorMsg = "⚠️ Failed to get fresh GPS lock. Make sure Location is on, and you have sky visibility."
        if (isPrivate && targetName != null) {
            if (!useCases.sendPrivateMessage(targetName, errorMsg, null, null, null, null)) {
                reportPrivateSendFailure()
            }
        } else {
            useCases.sendPublicMessage(errorMsg, null, null, null, null)
        }
    }
}
