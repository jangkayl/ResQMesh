package edu.cit.resqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cit.resqmesh.feature.comms.model.ChatUiState
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
import edu.cit.resqmesh.core.model.toChatMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import edu.cit.resqmesh.core.utils.SCAN_UI_THROTTLE_MS
import edu.cit.resqmesh.core.utils.throttleLatest
import edu.cit.resqmesh.core.domain.usecase.messaging.PrivateSendResult
import edu.cit.resqmesh.core.domain.usecase.messaging.SendPrivateChatMessageUseCase
import edu.cit.resqmesh.core.domain.usecase.sos.RaiseSosUseCase
import edu.cit.resqmesh.core.network.ratelimit.ChatBudgetState
import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import edu.cit.resqmesh.core.model.SosRaiseResult
import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget
import edu.cit.resqmesh.core.network.ratelimit.SosBudgetState
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextPolicy
import edu.cit.resqmesh.core.security.input.ValidationResult

@Suppress("LongParameterList")
class CommunicationViewModel(
    private val useCases: edu.cit.resqmesh.core.domain.usecase.MeshUseCases,
    private val locationClient: edu.cit.resqmesh.core.location.LocationClient,
    private val sosRepository: edu.cit.resqmesh.core.domain.repository.SosAlertRepository,
    private val conversationRepository: edu.cit.resqmesh.core.domain.repository.ConversationStateRepository,
    private val raiseSos: RaiseSosUseCase,
    private val sendPrivate: SendPrivateChatMessageUseCase,
    private val chatBudget: OutgoingChatBudget,
    private val sosBudget: OutgoingSosBudget,
    private val voiceHold: RadioVoiceHold
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
    val conversationStates =
        conversationRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sosMeshStatus = combine(useCases.observeIsOnline(), useCases.observeConnectedDevices(),
        useCases.observeBlockedDeviceNames()) { active, devices, blocked ->
        edu.cit.resqmesh.feature.comms.model.sosMeshStatus(active, devices, blocked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        edu.cit.resqmesh.feature.comms.model.SosMeshStatus(edu.cit.resqmesh.feature.comms.model.SosMeshState.OFF))
    private var sosLocationGeneration = 0L
    private var creatingSos = false
    private val sendDebouncer = SendDebouncer(SEND_DEBOUNCE_MS) { android.os.SystemClock.elapsedRealtime() }
    private val sendGate = ChatSendGate(chatBudget, sendDebouncer)

    /** Send budget for the composers. Ticks once a second only while collected and not full. */
    val chatBudgetState: StateFlow<ChatBudgetState> = channelFlow {
        chatBudget.refresh()
        launch { chatBudget.state.collect { send(it) } }
        chatBudget.state.map { it.remaining < it.capacity }.distinctUntilChanged().collectLatest { notFull ->
            while (notFull) {
                delay(BUDGET_TICK_MS)
                chatBudget.refresh()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(BUDGET_STOP_TIMEOUT_MS), chatBudget.state.value)

    private val sosGate = SosRaiseGate(sosBudget)

    /** SOS raise budget for the hub button. Ticks once a second only while collected and not full. */
    val sosBudgetState: StateFlow<SosBudgetState> = channelFlow {
        sosBudget.refresh()
        launch { sosBudget.state.collect { send(it) } }
        sosBudget.state.map { it.remaining < it.capacity }.distinctUntilChanged().collectLatest { notFull ->
            while (notFull) {
                delay(BUDGET_TICK_MS)
                sosBudget.refresh()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(BUDGET_STOP_TIMEOUT_MS), sosBudget.state.value)

    /** Tells the user when the next new SOS can be raised (the hub button was pressed with no token). */
    fun explainSosWait() {
        sosBudget.refresh()
        sosErrors.tryEmit(sosGate.waitMessage())
    }

    private fun mediaKind(image: String?, audio: String?) = when {
        image != null -> "img"
        audio != null -> "aud"
        else -> "txt"
    }

    /** Returns the sanitized text, or null (with feedback) when it exceeds the message limit. */
    private fun checkedMessageText(text: String): String? =
        when (val checked = TextPolicy.validate(text, TextField.MESSAGE)) {
            is ValidationResult.Valid -> checked.value
            ValidationResult.Blank -> ""
            is ValidationResult.TooLong -> {
                sosErrors.tryEmit("Message is too long (max ${checked.max} characters).")
                null
            }
        }

    private val _isAcquiringLocation = MutableStateFlow(false)
    val isAcquiringLocation: StateFlow<Boolean> = _isAcquiringLocation.asStateFlow()

    val locationStatus: StateFlow<edu.cit.resqmesh.core.location.LocationStatus> = locationClient.locationStatus
    
    val incomingSosAlert = sosRepository.incoming.map { it?.toChatMessage() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    fun clearSosAlert() {
        incomingSosAlert.value?.sosId?.let { silenceSos(it) }
    }
    
    val currentChannelId: StateFlow<String> = useCases.observeCurrentChannelId()
    
    fun setChannel(channelId: String) {
        useCases.setChannel(channelId)
    }

    fun draft(key: String, text: String) { viewModelScope.launch { conversationRepository.saveDraft(key, text) } }
    fun read(key: String) { viewModelScope.launch { conversationRepository.markRead(key, System.currentTimeMillis()) } }
    fun unread(key: String, messages: List<edu.cit.resqmesh.core.model.ChatMessage>): Int {
        val time = conversationStates.value.firstOrNull { it.conversationId == key }?.lastReadAt ?: 0L
        return messages.count { !it.isMine && it.timestamp > time }
    }
    /** Radio voice notes waiting for a send token (held app-wide, see [RadioVoiceHold]). */
    val voiceHeld: StateFlow<Int> = voiceHold.held

    /** Sends a radio voice note now or holds it for the next token; false only when the hold is full. */
    fun sendRadioVoice(channel: String, audio: String): Boolean = voiceHold.submit(channel, audio)

    /** Returns false only when the send budget is empty: nothing was sent and the caller must keep its text. */
    fun sendConversation(kind: String, channel: String = "", sosId: String = "", text: String, audio: String? = null): Boolean {
        val clean = checkedMessageText(text)
        val admission = if (clean == null) ChatSendGate.Admission.DEBOUNCED
        else sendGate.admit("conv:$kind:$channel:$sosId:${mediaKind(null, audio)}")
        if (clean != null && admission == ChatSendGate.Admission.GO) {
            // An empty id means the repository refused the message (final length guard): keep the draft.
            val messageId = useCases.sendPublicMessage.conversation(kind, channel, sosId, clean, audio)
            if (messageId.isNotEmpty()) {
                draft(edu.cit.resqmesh.core.model.ConversationPolicy.key(kind, channel, sosId), "")
            } else {
                sendGate.refund()
            }
        }
        return admission != ChatSendGate.Admission.NO_BUDGET
    }
    fun isOwnSos(alert: edu.cit.resqmesh.core.model.SosAlert): Boolean = sosRepository.isOwnSos(alert)
    fun sosActivity(id: String) = sosRepository.sosActivity(id)
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
            useCases.observeScannedDevices().throttleLatest(SCAN_UI_THROTTLE_MS).collect { devices ->
                _uiState.update { it.copy(scannedDevices = devices) }
            }
        }
        viewModelScope.launch {
            useCases.observeBlockedDeviceNames().collect { blocked ->
                _uiState.update { it.copy(blockedDeviceNames = blocked) }
            }
        }
    }

    /** Returns false only when the send budget is empty: nothing was sent and the caller must keep its text. */
    fun sendPublicMessage(text: String, imageBase64: String? = null, audioBase64: String? = null): Boolean {
        val clean = checkedMessageText(text)
        val admission = if (clean == null) ChatSendGate.Admission.DEBOUNCED
        else sendGate.admit("public:${mediaKind(imageBase64, audioBase64)}")
        if (clean != null && admission == ChatSendGate.Admission.GO) {
            sendGate.settle(useCases.sendPublicMessage(clean, imageBase64, audioBase64).isNotEmpty())
        }
        return admission != ChatSendGate.Admission.NO_BUDGET
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
                // The repository charges the budget under its lock, only when it creates a new sosId.
                val id = when (val result = raiseSos.raise(sosType, sosGate)) {
                    is SosRaiseResult.Created -> result.id
                    is SosRaiseResult.Reopened -> result.id
                    SosRaiseResult.Throttled -> {
                        sosErrors.emit(sosGate.waitMessage())
                        return@launch
                    }
                }
                onCreated(id)
                raiseSos.refineLocation(id, isCurrent = { generation == sosLocationGeneration }) { update ->
                    viewModelScope.launch { update() }
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

    /**
     * Returns true only when the message was sent. A duplicate tap inside the debounce window returns false
     * without feedback (the caller keeps its draft; the first tap's send is already in flight or done).
     */
    fun sendPrivateMessage(
        targetName: String,
        text: String,
        imageBase64: String? = null,
        audioBase64: String? = null
    ): Boolean {
        val key = "private:$targetName:${mediaKind(imageBase64, audioBase64)}"
        // No token: send nothing, keep the draft and show no error; the composer shows the wait.
        if (sendGate.admit(key, markNow = false) != ChatSendGate.Admission.GO) return false
        val result = sendPrivate(targetName, text, imageBase64, audioBase64)
        if (result !is PrivateSendResult.TooLong) sendGate.mark(key)
        if (result != PrivateSendResult.Sent) sendGate.refund()
        return when (result) {
            PrivateSendResult.Sent -> true
            PrivateSendResult.NeedsKeyVerification -> {
                _pendingKeyVerification.tryEmit(targetName)
                false
            }
            PrivateSendResult.Failed -> {
                reportPrivateSendFailure()
                false
            }
            is PrivateSendResult.TooLong -> {
                _privateSendErrors.tryEmit("Message is too long (max ${result.max} characters).")
                false
            }
        }
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
        if (!chatBudget.tryConsume()) return
        if (isPrivate && targetName != null) {
            if (!useCases.sendPrivateMessage(targetName, "📍 I am sharing my location.", null, null, location.latitude, location.longitude)) {
                chatBudget.refund()
                reportPrivateSendFailure()
            }
        } else {
            sendGate.settle(
                useCases.sendPublicMessage("📍 I am sharing my location.", null, null, location.latitude, location.longitude)
                    .isNotEmpty()
            )
        }
    }

    private fun sendLocationError(isPrivate: Boolean, targetName: String?) {
        val errorMsg = "⚠️ Failed to get fresh GPS lock. Make sure Location is on, and you have sky visibility."
        if (!chatBudget.tryConsume()) return
        if (isPrivate && targetName != null) {
            if (!useCases.sendPrivateMessage(targetName, errorMsg, null, null, null, null)) {
                chatBudget.refund()
                reportPrivateSendFailure()
            }
        } else {
            sendGate.settle(useCases.sendPublicMessage(errorMsg, null, null, null, null).isNotEmpty())
        }
    }
}

private const val SEND_DEBOUNCE_MS = 400L
private const val BUDGET_TICK_MS = 1_000L
private const val BUDGET_STOP_TIMEOUT_MS = 5_000L
