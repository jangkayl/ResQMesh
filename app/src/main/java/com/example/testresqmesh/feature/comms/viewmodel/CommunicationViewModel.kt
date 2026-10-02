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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.example.testresqmesh.data.repository.asMessage
import kotlinx.coroutines.launch

class CommunicationViewModel(
    private val useCases: com.example.testresqmesh.core.domain.usecase.MeshUseCases,
    private val locationClient: com.example.testresqmesh.core.location.LocationClient,
    val sosRepository: com.example.testresqmesh.data.repository.SosRepository,
    private val conversationDao: com.example.testresqmesh.data.local.dao.ConversationStateDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _privateSendErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val privateSendErrors: SharedFlow<String> = _privateSendErrors
    private val _pendingKeyVerification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingKeyVerification: SharedFlow<String> = _pendingKeyVerification
    private val _privateDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val sosErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val privateDrafts: StateFlow<Map<String, String>> = _privateDrafts.asStateFlow()
    
    val activeSosMessageId = sosRepository.ownActive.map { it?.sosId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sosAlerts = sosRepository.alerts
    val allPublicMessages = useCases.observePublicMessages.allMessages
    val conversationStates = conversationDao.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sosMeshStatus = combine(useCases.observeIsOnline(), useCases.observeConnectedDevices(),
        useCases.observeBlockedDeviceNames()) { active, devices, blocked ->
        com.example.testresqmesh.feature.comms.model.sosMeshStatus(active, devices, blocked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        com.example.testresqmesh.feature.comms.model.SosMeshStatus(com.example.testresqmesh.feature.comms.model.SosMeshState.OFF))
    private var sosLocationGeneration = 0L
    private var creatingSos = false

    private val _isAcquiringLocation = MutableStateFlow(false)
    val isAcquiringLocation: StateFlow<Boolean> = _isAcquiringLocation.asStateFlow()

    val locationStatus: StateFlow<com.example.testresqmesh.core.location.LocationStatus> = locationClient.locationStatus
    
    val incomingSosAlert = sosRepository.incoming.map { it?.asMessage() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    fun clearSosAlert() {
        incomingSosAlert.value?.sosId?.let { silenceSos(it) }
    }
    
    val currentChannelId: StateFlow<String> = useCases.observeCurrentChannelId()
    
    fun setChannel(channelId: String) {
        useCases.setChannel(channelId)
    }

    fun draft(key: String, text: String) { viewModelScope.launch { conversationDao.draft(key, text) } }
    fun read(key: String) { viewModelScope.launch { conversationDao.read(key, System.currentTimeMillis()) } }
    fun unread(key: String, messages: List<com.example.testresqmesh.core.model.ChatMessage>): Int {
        val time = conversationStates.value.firstOrNull { it.conversationId == key }?.lastReadAt ?: 0L
        return messages.count { !it.isMine && it.timestamp > time }
    }
    fun sendConversation(kind: String, channel: String = "", sosId: String = "", text: String, audio: String? = null) {
        useCases.sendPublicMessage.conversation(kind, channel, sosId, text, audio)
        draft(com.example.testresqmesh.core.model.ConversationPolicy.key(kind, channel, sosId), "")
    }
    fun silenceSos(id: String) { viewModelScope.launch { sosRepository.silence(id) } }
    fun endSos(id: String) {
        sosLocationGeneration++
        locationClient.cancelPinpointLocation()
        viewModelScope.launch {
            runCatching { sosRepository.end(id) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                sosErrors.emit("Could not end SOS. Check its active status and try again.")
            }
        }
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
            useCases.observePeerNames().collect { names ->
                _uiState.update { it.copy(peerNames = names) }
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

    val publicSendFeedback = kotlinx.coroutines.flow.merge(useCases.sendPublicMessage.feedback, sosErrors)

    fun startLocationTracking() {
        locationClient.startTracking(30000L)
    }
    
    fun stopLocationTracking() {
        locationClient.stopTracking()
    }

    fun sendEmergencySOS(sosType: String, onCreated: (String) -> Unit = {}) {
        if (creatingSos) return
        creatingSos = true
        val generation = ++sosLocationGeneration
        viewModelScope.launch {
            try {
                val cached = locationClient.getLastKnownLocation()
                val validCachedTime = cached?.time?.takeIf { it > 0 && (System.currentTimeMillis() - it) < 15 * 60 * 1000L }
                val id = sosRepository.create(sosType, cached?.latitude, cached?.longitude, cached?.accuracy, validCachedTime)
                onCreated(id)
                locationClient.requestPinpointLocation { location ->
                    if (generation == sosLocationGeneration && location != null) viewModelScope.launch {
                        val validTime = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                        sosRepository.updateLocation(id, location.latitude, location.longitude, location.accuracy, validTime)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                sosErrors.emit("Could not complete SOS sending. Check SOS alerts before trying again.")
            } finally { creatingSos = false }
        }
    }

    fun cancelEmergencySOS() {
        activeSosMessageId.value?.let(::endSos)
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

    fun rejectPendingPublicKeyChange(targetName: String): Boolean = useCases.rejectPendingPublicKeyChange(targetName)

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
        _isAcquiringLocation.value = true
        // The BEST way to get location on Android (Handles indoors via Wi-Fi/Cell + outdoors via GPS seamlessly)
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context.applicationContext)
        
        try {
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                null
            ).addOnSuccessListener { location ->
                _isAcquiringLocation.value = false
                if (location != null) {
                    sendLocationMessage(location, isPrivate, targetName)
                } else {
                    // Fallback to cache if fresh fetch miraculously fails
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        _isAcquiringLocation.value = false
                        if (lastLoc != null) {
                            sendLocationMessage(lastLoc, isPrivate, targetName)
                        } else {
                            sendLocationError(isPrivate, targetName)
                        }
                    }.addOnFailureListener {
                        _isAcquiringLocation.value = false
                        sendLocationError(isPrivate, targetName)
                    }
                }
            }.addOnFailureListener {
                _isAcquiringLocation.value = false
                sendLocationError(isPrivate, targetName)
            }
        } catch (e: SecurityException) {
            _isAcquiringLocation.value = false
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
