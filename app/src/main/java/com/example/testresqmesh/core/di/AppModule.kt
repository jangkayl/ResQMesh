package com.example.testresqmesh.core.di

import com.example.testresqmesh.core.domain.usecase.MeshUseCases
import com.example.testresqmesh.core.domain.usecase.*
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.utils.LiveAudioEngine
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.location.DefaultLocationClient
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { NativeBleManager(androidContext()) }
    single { AppDatabase.getDatabase(androidContext()) }
    single { MeshRepository(get(), get()) }
    single { MediaHelper(androidContext()) }
    single<LocationClient> { DefaultLocationClient(androidContext()) }
    
    single { 
        val repository = get<MeshRepository>()
        LiveAudioEngine(androidContext()) { chunk ->
            repository.broadcastLiveAudioChunk(chunk)
        }
    }

    single {
        MeshUseCases(
            startNode = StartNodeUseCase(get()),
            stopNode = StopNodeUseCase(get()),
            setChannel = SetChannelUseCase(get()),
            disconnectDevice = DisconnectDeviceUseCase(get()),
            blockDevice = BlockDeviceUseCase(get()),
            unblockDevice = UnblockDeviceUseCase(get()),
            forceConnect = ForceConnectUseCase(get()),
            rescan = RescanUseCase(get()),
            sendPublicMessage = SendPublicMessageUseCase(get()),
            sendPrivateMessage = SendPrivateMessageUseCase(get()),
            deleteConversationWith = DeleteConversationWithUseCase(get()),
            broadcastLiveAudioChunk = BroadcastLiveAudioChunkUseCase(get()),
            broadcastSeenReceipt = BroadcastSeenReceiptUseCase(get()),
            clearSosAlert = ClearSosAlertUseCase(get()),
            observeConnectionStatus = ObserveConnectionStatusUseCase(get()),
            observeIsOnline = ObserveIsOnlineUseCase(get()),
            observeCurrentChannelId = ObserveCurrentChannelIdUseCase(get()),
            observeConnectedDevices = ObserveConnectedDevicesUseCase(get()),
            observeScannedDevices = ObserveScannedDevicesUseCase(get()),
            observeIncomingSosAlert = ObserveIncomingSosAlertUseCase(get()),
            observeIncomingVoiceMessage = ObserveIncomingVoiceMessageUseCase(get()),
            observeIncomingLiveAudioChunk = ObserveIncomingLiveAudioChunkUseCase(get()),
            observeKnownNodes = ObserveKnownNodesUseCase(get()),
            observeTopology = ObserveTopologyUseCase(get()),
            observePublicMessages = ObservePublicMessagesUseCase(get()),
            observePrivateMessages = ObservePrivateMessagesUseCase(get()),
            observeBlockedDeviceNames = ObserveBlockedDeviceNamesUseCase(get())
        )
    }

    viewModel { SetupViewModel(get()) }
    viewModel { RadarViewModel(get()) }
    viewModel { CommunicationViewModel(get(), get()) }
    viewModel { WalkieTalkieViewModel(get(), get(), get()) }
}
