package edu.cit.resqmesh.app.di

import edu.cit.resqmesh.core.domain.repository.ConversationStateRepository
import edu.cit.resqmesh.core.domain.repository.IncidentFeedRepository
import edu.cit.resqmesh.core.domain.repository.LocalUserRepository
import edu.cit.resqmesh.core.domain.repository.MeshNodeRepository
import edu.cit.resqmesh.core.domain.repository.SosAlertRepository
import edu.cit.resqmesh.core.domain.usecase.*
import edu.cit.resqmesh.core.domain.usecase.sos.RaiseSosUseCase
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.location.LocationClientFactory
import edu.cit.resqmesh.core.map.download.AndroidConnectivityProvider
import edu.cit.resqmesh.core.map.download.ConnectivityProvider
import edu.cit.resqmesh.core.map.download.MapPackageDownloader
import edu.cit.resqmesh.core.map.storage.MapStorageGuard
import edu.cit.resqmesh.core.map.verifier.ManifestVerifier
import edu.cit.resqmesh.core.network.MeshNetworkGateway
import edu.cit.resqmesh.core.network.NativeBleGateway
import edu.cit.resqmesh.core.network.NativeBleManager
import edu.cit.resqmesh.core.network.ratelimit.InboundGuards
import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import edu.cit.resqmesh.core.network.ratelimit.OutgoingSosBudget
import edu.cit.resqmesh.feature.comms.viewmodel.RadioVoiceHold
import edu.cit.resqmesh.core.network.ratelimit.RateLimitPolicy
import edu.cit.resqmesh.core.service.MeshSessionController
import edu.cit.resqmesh.core.audio.LiveAudioEngine
import edu.cit.resqmesh.core.audio.MediaHelper
import edu.cit.resqmesh.core.notification.NotificationHelper
import edu.cit.resqmesh.core.notification.SosAlertNotifier
import edu.cit.resqmesh.feature.sos.alert.SosAlertController
import edu.cit.resqmesh.data.local.AppDatabase
import edu.cit.resqmesh.data.location.DefaultLocationClient
import edu.cit.resqmesh.data.location.DefaultLocationClientFactory
import edu.cit.resqmesh.data.repository.BlockRelationshipStore
import edu.cit.resqmesh.data.repository.IdentityProvider
import edu.cit.resqmesh.data.repository.IncidentEventSigning
import edu.cit.resqmesh.data.repository.IncidentRepository
import edu.cit.resqmesh.data.repository.KeystoreIncidentEventSigning
import edu.cit.resqmesh.data.repository.LocalIdentityManager
import edu.cit.resqmesh.data.repository.MeshReadyPeerEvents
import edu.cit.resqmesh.data.repository.MeshRepository
import edu.cit.resqmesh.data.repository.MessageStore
import edu.cit.resqmesh.data.repository.PeerNameStore
import edu.cit.resqmesh.data.repository.PeerPublicKeyDirectory
import edu.cit.resqmesh.data.repository.RoomMessageStore
import edu.cit.resqmesh.data.repository.RoomPeerNameStore
import edu.cit.resqmesh.data.repository.RoomSosStore
import edu.cit.resqmesh.data.repository.SosAlertSink
import edu.cit.resqmesh.data.repository.SosRepository
import edu.cit.resqmesh.data.repository.SosStore
import edu.cit.resqmesh.data.repository.adapter.ConversationStateRepositoryAdapter
import edu.cit.resqmesh.data.repository.adapter.IncidentFeedRepositoryAdapter
import edu.cit.resqmesh.data.repository.adapter.LocalUserRepositoryAdapter
import edu.cit.resqmesh.data.repository.adapter.MeshNodeRepositoryAdapter
import edu.cit.resqmesh.data.repository.adapter.SosAlertRepositoryAdapter
import edu.cit.resqmesh.data.repository.adapter.SosAlertSinkAdapter
import edu.cit.resqmesh.feature.comms.viewmodel.CommunicationViewModel
import edu.cit.resqmesh.feature.comms.viewmodel.WalkieTalkieViewModel
import edu.cit.resqmesh.feature.incident.viewmodel.IncidentViewModel
import edu.cit.resqmesh.feature.profile.viewmodel.AboutViewModel
import edu.cit.resqmesh.feature.profile.viewmodel.OfflineMapViewModel
import edu.cit.resqmesh.feature.radar.viewmodel.RadarViewModel
import edu.cit.resqmesh.feature.setup.viewmodel.SetupViewModel
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
    single { edu.cit.resqmesh.core.location.LocationPreferences(androidContext()) }
    single { edu.cit.resqmesh.core.notification.NotificationPreferences(androidContext()) }
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
    single { RateLimitPolicy() }
    single { InboundGuards(get(), System::currentTimeMillis) }
    single { OutgoingChatBudget(get(), System::currentTimeMillis) }
    single { OutgoingSosBudget(get(), System::currentTimeMillis) }
    single { NativeBleManager(androidContext(), get()) }
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
            sosRepository = get(),
            chatBudget = get()
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
            sink = get(),
            originGate = get<InboundGuards>().origins
        )
    }
    single<edu.cit.resqmesh.core.notification.IncidentNotifier> {
        edu.cit.resqmesh.feature.incident.alert.IncidentAlertController(get(), get())
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
            readyPeerEvents = get(),
            incidentNotifier = get()
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
    single { edu.cit.resqmesh.core.domain.usecase.messaging.SendPrivateChatMessageUseCase(get()) }
}

/** ViewModels. */
val viewModelModule = module {
    single {
        val useCases = get<MeshUseCases>()
        RadioVoiceHold(get(), get<AppCoroutineScope>().scope) { channel, audio ->
            useCases.sendPublicMessage.conversation("RADIO", channel, "", "Voice message", audio).isNotEmpty()
        }
    }
    viewModel {
        SetupViewModel(
            useCases = get(),
            identityProvider = get(),
            meshSessionController = get(),
            locationPreferences = get(),
            notificationPreferences = get()
        )
    }
    viewModel { RadarViewModel(get()) }
    viewModel {
        CommunicationViewModel(
            useCases = get(),
            locationClient = get(),
            sosRepository = get(),
            conversationRepository = get(),
            raiseSos = get(),
            sendPrivate = get(),
            chatBudget = get(),
            sosBudget = get(),
            voiceHold = get(),
            locationPreferences = get()
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
