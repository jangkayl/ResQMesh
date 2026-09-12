package com.example.testresqmesh.feature.comms.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WalkieTalkieViewModel(
    private val repository: MeshRepository,
    private val mediaHelper: MediaHelper
) : ViewModel() {

    private val _isWalkieTalkieMode = MutableStateFlow(false)
    val isWalkieTalkieMode = _isWalkieTalkieMode.asStateFlow()

    val currentChannelId: StateFlow<String> = repository.currentChannelId
    
    fun setChannel(channelId: String) {
        repository.setChannel(channelId)
    }

    init {
        viewModelScope.launch {
            repository.incomingVoiceMessage.collect { message ->
                if (_isWalkieTalkieMode.value) {
                    message.audioBase64?.let { base64 ->
                        // Automatically play the voice note
                        mediaHelper.playVoiceMail(base64)
                        
                        // Mark it as seen since we listened to it automatically
                        repository.broadcastSeenReceipt(message.id, message.isPrivate, message.senderName)
                    }
                }
            }
        }
    }

    fun toggleWalkieTalkieMode() {
        _isWalkieTalkieMode.value = !_isWalkieTalkieMode.value
    }
}
