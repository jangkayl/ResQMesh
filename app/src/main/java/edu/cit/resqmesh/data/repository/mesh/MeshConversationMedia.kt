package edu.cit.resqmesh.data.repository.mesh

import edu.cit.resqmesh.core.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow

internal class MeshConversationMedia(
    private val _currentChannelId: MutableStateFlow<String>,
    private val _incomingSosAlert: MutableStateFlow<ChatMessage?>
) {
    fun onNetworkSosCancelled(): Unit = run { /* Unscoped legacy cancellation is ignored. */ }

    fun setChannel(channelId: String) {
        _currentChannelId.value = channelId
    }

    fun clearSosAlert() {
        _incomingSosAlert.value = null
    }
}
