package com.example.testresqmesh.app.di

import com.example.testresqmesh.core.domain.repository.ConversationStateRepository
import com.example.testresqmesh.core.domain.repository.IncidentFeedRepository
import com.example.testresqmesh.core.domain.repository.LocalUserRepository
import com.example.testresqmesh.core.domain.repository.MeshNodeRepository
import com.example.testresqmesh.core.domain.repository.SosAlertRepository
import com.example.testresqmesh.core.domain.usecase.*
import com.example.testresqmesh.core.domain.usecase.sos.RaiseSosUseCase
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.location.LocationClientFactory
import com.example.testresqmesh.core.map.download.AndroidConnectivityProvider
import com.example.testresqmesh.core.map.download.ConnectivityProvider
import com.example.testresqmesh.core.map.download.MapPackageDownloader
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.map.verifier.ManifestVerifier
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.NativeBleGateway
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.service.MeshSessionController
import com.example.testresqmesh.core.audio.LiveAudioEngine
import com.example.testresqmesh.core.audio.MediaHelper
import com.example.testresqmesh.core.notification.NotificationHelper
import com.example.testresqmesh.core.notification.SosAlertNotifier
import com.example.testresqmesh.feature.sos.alert.SosAlertController
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.location.DefaultLocationClient
import com.example.testresqmesh.data.location.DefaultLocationClientFactory
import com.example.testresqmesh.data.repository.BlockRelationshipStore
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentEventSigning
import com.example.testresqmesh.data.repository.IncidentRepository
import com.example.testresqmesh.data.repository.KeystoreIncidentEventSigning
import com.example.testresqmesh.data.repository.LocalIdentityManager
import com.example.testresqmesh.data.repository.MeshReadyPeerEvents
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.data.repository.MessageStore
import com.example.testresqmesh.data.repository.PeerNameStore
import com.example.testresqmesh.data.repository.PeerPublicKeyDirectory
import com.example.testresqmesh.data.repository.RoomMessageStore
import com.example.testresqmesh.data.repository.RoomPeerNameStore
import com.example.testresqmesh.data.repository.RoomSosStore
import com.example.testresqmesh.data.repository.SosAlertSink
import com.example.testresqmesh.data.repository.SosRepository
import com.example.testresqmesh.data.repository.SosStore
import com.example.testresqmesh.data.repository.adapter.ConversationStateRepositoryAdapter
import com.example.testresqmesh.data.repository.adapter.IncidentFeedRepositoryAdapter
import com.example.testresqmesh.data.repository.adapter.LocalUserRepositoryAdapter
import com.example.testresqmesh.data.repository.adapter.MeshNodeRepositoryAdapter
import com.example.testresqmesh.data.repository.adapter.SosAlertRepositoryAdapter
import com.example.testresqmesh.data.repository.adapter.SosAlertSinkAdapter
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel
import com.example.testresqmesh.feature.profile.viewmodel.AboutViewModel
import com.example.testresqmesh.feature.profile.viewmodel.OfflineMapViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/*
 * Koin composition root, split by layer.
 *
 * Ordering matters: [appModules] is loaded in list order and Koin creates createdAtStart
 * singletons in definition order. Eager order is: AppCoroutineScope, MeshReadyPeerEvents,
 * MapPackageDownloader (platformModule), then SosRepository, IncidentRepository
 * (repositoryModule). Each gateway on-callback slot has exactly one owner, assigned in that
 * owner's init (MeshRepository, SosRepository, IncidentRepository); do not add another.
 */

/** Platform/session/location/map services and the shared app scope. */
val platformModule = module {
    single(createdAtStart = true) { AppCoroutineScope(Dispatchers.IO) }
    single(createdAtStart = true) { MeshReadyPeerEvents() }
    single { MeshSessionController(androidContext(), get()) }
    single { MediaHelper(androidContext()) }
    single { NotificationHelper(androidContext()) }
    single<LocationClient> { DefaultLocationClient(androidContext()) }
    single<LocationClientFactory> { DefaultLocationClientFactory(androidContext()) }

    // Offline map package management
    single { MapStorageGuard(androidContext()) }
    single { ManifestVerifier() }
    single<ConnectivityProvider> { AndroidConnectivityProvider(androidContext()) }
    single(createdAtStart = true) {
        MapPackageDownloader(
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
}

/** BLE transport. */
val networkModule = module {
    single { NativeBleManager(androidContext()) }
    single<MeshNetworkGateway> { NativeBleGateway(get()) }
}

/** Room database. */
val databaseModule = module {
    single { AppDatabase.getDatabase(androidContext()) }
}

/** Stores, repositories and identity. */
val repositoryModule = module {
    single<MessageStore> { RoomMessageStore(get<AppDatabase>().messageDao()) }
    single<PeerNameStore> { RoomPeerNameStore(get<AppDatabase>().peerNameDao()) }
    single { BlockRelationshipStore(androidContext()) }
    single { PeerPublicKeyDirectory(androidContext()) }
    single {
        MeshRepository(
            networkManager = get(),
            messageStore = get(),
            blockStore = get(),
            publicKeys = get(),
            repositoryScope = get<AppCoroutineScope>().scope,
            readyPeerEvents = get(),
            peerNameStore = get(),
            sosRepository = get()
        )
    }

    single { LocalIdentityManager(androidContext(), get<AppDatabase>().userDao()) }
    single<IdentityProvider> { get<LocalIdentityManager>() }
    single<IncidentEventSigning> { KeystoreIncidentEventSigning() }
    single<SosStore> { RoomSosStore(get()) }
    single<SosAlertNotifier> {
        SosAlertController(androidContext(), get(), get(), get<AppCoroutineScope>().scope)
    }
    single<SosAlertSink> { SosAlertSinkAdapter(get()) }
    single(createdAtStart = true) {
        SosRepository(
            store = get(),
            signing = get(),
            identity = get(),
            gateway = get(),
            peers = get(),
            scope = get<AppCoroutineScope>().scope,
            sink = get()
        )
    }
    single(createdAtStart = true) {
        IncidentRepository(
            incidentDao = get<AppDatabase>().incidentDao(),
            domainEventDao = get<AppDatabase>().domainEventDao(),
            offerDao = get<AppDatabase>().incidentOfferDao(),
            database = get<AppDatabase>(),
            eventSigning = get(),
            identityManager = get(),
            networkGateway = get(),
            repositoryScope = get<AppCoroutineScope>().scope,
            readyPeerEvents = get()
        )
    }
}

/** Domain repository interfaces over the existing repositories; lazy, so eager order is unchanged. */
val adapterModule = module {
    single<MeshNodeRepository> { MeshNodeRepositoryAdapter(get()) }
    single<SosAlertRepository> { SosAlertRepositoryAdapter(get(), get<AppCoroutineScope>().scope) }
    single<IncidentFeedRepository> { IncidentFeedRepositoryAdapter(get()) }
    single<LocalUserRepository> { LocalUserRepositoryAdapter(get()) }
    single<ConversationStateRepository> {
        ConversationStateRepositoryAdapter(get<AppDatabase>().conversationStateDao())
    }
}

/** Use cases. */
val domainModule = module {
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
            observePeerNames = ObservePeerNamesUseCase(get()),
            observeBlockedDeviceNames = ObserveBlockedDeviceNamesUseCase(get())
        )
    }
    single { RaiseSosUseCase(get(), get()) }
    single { com.example.testresqmesh.core.domain.usecase.messaging.SendPrivateChatMessageUseCase(get()) }
}

/** ViewModels. */
val viewModelModule = module {
    viewModel { SetupViewModel(useCases = get(), identityProvider = get(), meshSessionController = get()) }
    viewModel { RadarViewModel(get()) }
    viewModel {
        CommunicationViewModel(
            useCases = get(),
            locationClient = get(),
            sosRepository = get(),
            conversationRepository = get(),
            raiseSos = get(),
            sendPrivate = get()
        )
    }
    viewModel { WalkieTalkieViewModel(useCases = get(), mediaHelper = get(), liveAudioEngine = get()) }
    viewModel { OfflineMapViewModel(storageGuard = get(), downloader = get(), connectivityProvider = get()) }
    viewModel { AboutViewModel() }
    viewModel { IncidentViewModel(incidentRepository = get(), identityManager = get()) }
}

val appModules = listOf(
    platformModule,
    networkModule,
    databaseModule,
    repositoryModule,
    adapterModule,
    domainModule,
    viewModelModule
)
