package com.example.testresqmesh.core.domain.usecase

import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.ScannedDevice
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class MeshUseCases(
    val startNode: StartNodeUseCase,
    val stopNode: StopNodeUseCase,
    val setChannel: SetChannelUseCase,
    val disconnectDevice: DisconnectDeviceUseCase,
    val blockDevice: BlockDeviceUseCase,
    val unblockDevice: UnblockDeviceUseCase,
    val forceConnect: ForceConnectUseCase,
    val rescan: RescanUseCase,
    val sendPublicMessage: SendPublicMessageUseCase,
    val sendPrivateMessage: SendPrivateMessageUseCase,
    val deleteConversationWith: DeleteConversationWithUseCase,
    val broadcastLiveAudioChunk: BroadcastLiveAudioChunkUseCase,
    val broadcastSeenReceipt: BroadcastSeenReceiptUseCase,
    val clearSosAlert: ClearSosAlertUseCase,

    val observeConnectionStatus: ObserveConnectionStatusUseCase,
    val observeIsOnline: ObserveIsOnlineUseCase,
    val observeCurrentChannelId: ObserveCurrentChannelIdUseCase,
    val observeConnectedDevices: ObserveConnectedDevicesUseCase,
    val observeScannedDevices: ObserveScannedDevicesUseCase,
    val observeIncomingSosAlert: ObserveIncomingSosAlertUseCase,
    val observeIncomingVoiceMessage: ObserveIncomingVoiceMessageUseCase,
    val observeIncomingLiveAudioChunk: ObserveIncomingLiveAudioChunkUseCase,
    val observeKnownNodes: ObserveKnownNodesUseCase,
    val observeTopology: ObserveTopologyUseCase,
    val observePublicMessages: ObservePublicMessagesUseCase,
    val observePrivateMessages: ObservePrivateMessagesUseCase,
    val observeBlockedDeviceNames: ObserveBlockedDeviceNamesUseCase
)

class StartNodeUseCase(private val repository: MeshRepository) {
    operator fun invoke(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        repository.startNode(customName, nodeTag, teamKey, nodeId)
    }
}

class StopNodeUseCase(private val repository: MeshRepository) {
    operator fun invoke() {
        repository.stopNode()
    }
}

class SetChannelUseCase(private val repository: MeshRepository) {
    operator fun invoke(channelId: String) {
        repository.setChannel(channelId)
    }
}

class DisconnectDeviceUseCase(private val repository: MeshRepository) {
    operator fun invoke(endpointId: String) {
        repository.disconnectDevice(endpointId)
    }
}

class BlockDeviceUseCase(private val repository: MeshRepository) {
    operator fun invoke(deviceName: String) {
        repository.blockDevice(deviceName)
    }
}

class UnblockDeviceUseCase(private val repository: MeshRepository) {
    operator fun invoke(deviceName: String) {
        repository.unblockDevice(deviceName)
    }
}

class ForceConnectUseCase(private val repository: MeshRepository) {
    operator fun invoke(endpointId: String, endpointName: String) {
        repository.forceConnect(endpointId, endpointName)
    }
}

class RescanUseCase(private val repository: MeshRepository) {
    operator fun invoke() {
        repository.rescan()
    }
}

class SendPublicMessageUseCase(private val repository: MeshRepository) {
    operator fun invoke(text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null, isSOS: Boolean = false, isSOSCancel: Boolean = false): String {
        return repository.sendPublicMessage(text, imageBase64, audioBase64, locationLat, locationLng, isSOS, isSOSCancel)
    }
}

class SendPrivateMessageUseCase(private val repository: MeshRepository) {
    operator fun invoke(targetName: String, text: String, imageBase64: String?, audioBase64: String?, locationLat: Double? = null, locationLng: Double? = null) {
        repository.sendPrivateMessage(targetName, text, imageBase64, audioBase64, locationLat, locationLng)
    }
}

class DeleteConversationWithUseCase(private val repository: MeshRepository) {
    operator fun invoke(peerName: String) {
        repository.deleteConversationWith(peerName)
    }
}

class BroadcastLiveAudioChunkUseCase(private val repository: MeshRepository) {
    operator fun invoke(chunk: ByteArray) {
        repository.broadcastLiveAudioChunk(chunk)
    }
}

class BroadcastSeenReceiptUseCase(private val repository: MeshRepository) {
    operator fun invoke(messageId: String, isPrivate: Boolean, targetName: String? = null) {
        repository.broadcastSeenReceipt(messageId, isPrivate, targetName)
    }
}

class ClearSosAlertUseCase(private val repository: MeshRepository) {
    operator fun invoke() {
        repository.clearSosAlert()
    }
}

class ObserveConnectionStatusUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<String> = repository.connectionStatus
}

class ObserveIsOnlineUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.isOnline
}

class ObserveCurrentChannelIdUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<String> = repository.currentChannelId
}

class ObserveConnectedDevicesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<List<ConnectedDevice>> = repository.connectedDevices
}

class ObserveScannedDevicesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<List<ScannedDevice>> = repository.scannedDevices
}

class ObserveIncomingSosAlertUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<ChatMessage?> = repository.incomingSosAlert
}

class ObserveIncomingVoiceMessageUseCase(private val repository: MeshRepository) {
    operator fun invoke(): SharedFlow<ChatMessage> = repository.incomingVoiceMessage
}

class ObserveIncomingLiveAudioChunkUseCase(private val repository: MeshRepository) {
    operator fun invoke(): SharedFlow<Pair<String, ByteArray>> = repository.incomingLiveAudioChunk
}

class ObserveKnownNodesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<List<KnownNode>> = repository.knownNodes
}

class ObserveTopologyUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<Map<String, Set<String>>> = repository.topology
}

class ObservePublicMessagesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<List<ChatMessage>> = repository.publicMessages
}

class ObservePrivateMessagesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<Map<String, List<ChatMessage>>> = repository.privateMessages
}

class ObserveBlockedDeviceNamesUseCase(private val repository: MeshRepository) {
    operator fun invoke(): StateFlow<Set<String>> = repository.blockedDeviceNames
}
