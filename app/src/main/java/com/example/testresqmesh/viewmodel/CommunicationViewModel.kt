package com.example.testresqmesh.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.data.models.ConnectedDevice
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.ui.states.ChatUiState
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
    }

    fun sendPublicMessage(text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPublicMessage(text, imageBase64, audioBase64)
    }

    fun sendPrivateMessage(target: ConnectedDevice, text: String, imageBase64: String? = null, audioBase64: String? = null) {
        repository.sendPrivateMessage(target, text, imageBase64, audioBase64)
    }
}
