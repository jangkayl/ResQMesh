package com.example.testresqmesh.core.di

import com.example.testresqmesh.core.domain.usecase.MeshUseCases
import com.example.testresqmesh.core.domain.usecase.*
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.NativeBleGateway
import com.example.testresqmesh.core.utils.LiveAudioEngine
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.location.DefaultLocationClient
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.data.repository.MessageStore
import com.example.testresqmesh.data.repository.RoomMessageStore
import com.example.testresqmesh.data.repository.BlockRelationshipStore
import com.example.testresqmesh.data.repository.PeerPublicKeyDirectory
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.data.repository.LocalIdentityManager
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentRepository
import com.example.testresqmesh.data.repository.MeshReadyPeerEvents
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { NativeBleManager(androidContext()) }
    single<MeshNetworkGateway> { NativeBleGateway(get()) }
    single { AppDatabase.getDatabase(androidContext()) }
    single<MessageStore> { RoomMessageStore(get<AppDatabase>().messageDao()) }
    single { BlockRelationshipStore(androidContext()) }
    single { PeerPublicKeyDirectory(androidContext()) }
    single(createdAtStart = true) { AppCoroutineScope(Dispatchers.IO) }
    single(createdAtStart = true) { MeshReadyPeerEvents() }
    single { MeshRepository(get(), get(), get(), get(), get<AppCoroutineScope>().scope, get()) }
    single { MediaHelper(androidContext()) }
    single { com.example.testresqmesh.core.utils.NotificationHelper(androidContext()) }
    single<LocationClient> { DefaultLocationClient(androidContext()) }
    
    // Offline map package management
    single { com.example.testresqmesh.core.map.storage.MapStorageGuard(androidContext()) }
    single { com.example.testresqmesh.core.map.verifier.ManifestVerifier() }
    single<com.example.testresqmesh.core.map.download.ConnectivityProvider> {
        com.example.testresqmesh.core.map.download.AndroidConnectivityProvider(androidContext())
    }
    single(createdAtStart = true) {
        com.example.testresqmesh.core.map.download.MapPackageDownloader(
            storageGuard = get(),
            verifier = get(),
            connectivityProvider = get()
        )
    }

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
            hasPendingPublicKeyChange = HasPendingPublicKeyChangeUseCase(get()),
            acceptPendingPublicKeyChange = AcceptPendingPublicKeyChangeUseCase(get()),
            rejectPendingPublicKeyChange = RejectPendingPublicKeyChangeUseCase(get()),
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

    viewModel { SetupViewModel(get(), get()) }
    viewModel { RadarViewModel(get()) }
    viewModel { CommunicationViewModel(get(), get()) }
    viewModel { WalkieTalkieViewModel(get(), get(), get()) }
    viewModel { com.example.testresqmesh.feature.profile.viewmodel.OfflineMapViewModel(get(), get(), get()) }

    single { LocalIdentityManager(androidContext(), get<AppDatabase>().userDao()) }
    single<IdentityProvider> { get<LocalIdentityManager>() }
    single {
        com.example.testresqmesh.data.repository.IncidentRepository(
            incidentDao = get<AppDatabase>().incidentDao(),
            domainEventDao = get<AppDatabase>().domainEventDao(),
            identityManager = get(),
            networkGateway = get(),
            repositoryScope = get<AppCoroutineScope>().scope,
            readyPeerEvents = get()
        )
    }
    viewModel { com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel(get(), get()) }
}
