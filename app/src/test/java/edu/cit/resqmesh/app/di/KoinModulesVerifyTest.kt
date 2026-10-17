package edu.cit.resqmesh.app.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import android.content.SharedPreferences
import edu.cit.resqmesh.core.domain.usecase.*
import edu.cit.resqmesh.core.map.catalog.MapPackageCatalogEntry
import edu.cit.resqmesh.core.map.download.HttpTransport
import edu.cit.resqmesh.core.map.verifier.SignatureVerifier
import edu.cit.resqmesh.data.local.dao.ConversationStateDao
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.dao.MessageDao
import edu.cit.resqmesh.data.local.dao.PeerNameDao
import edu.cit.resqmesh.data.local.dao.UserDao
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Static check that every constructor dependency declared by the app modules is resolvable.
 *
 * Koin's verify() inspects constructor parameter types of each definition, not the lambda body,
 * so it cannot see values the lambdas supply from expressions (androidContext(), the scope
 * extracted from AppCoroutineScope, and the `get<AppDatabase>().xxxDao()` calls). Those types,
 * plus defaulted/function-typed constructor parameters, are listed as extraTypes. It does not
 * instantiate anything, so Android framework behavior and eager (createdAtStart) wiring order
 * are not exercised here.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerifyTest {
    @Test
    fun appModulesDependencyGraphIsComplete() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                SavedStateHandle::class,
                CoroutineScope::class,
                Function0::class,
                Function1::class,
                MapPackageCatalogEntry::class,
                File::class,
                String::class,
                SharedPreferences::class,
                HttpTransport::class,
                SignatureVerifier::class,
                CoroutineDispatcher::class,
                // DAOs are supplied by get<AppDatabase>().xxxDao() inside definitions.
                UserDao::class,
                MessageDao::class,
                PeerNameDao::class,
                IncidentDao::class,
                DomainEventDao::class,
                IncidentOfferDao::class,
                ConversationStateDao::class,
                // Use cases are constructed inline inside the MeshUseCases definition.
                StartNodeUseCase::class,
                StopNodeUseCase::class,
                SetChannelUseCase::class,
                DisconnectDeviceUseCase::class,
                BlockDeviceUseCase::class,
                UnblockDeviceUseCase::class,
                ForceConnectUseCase::class,
                RescanUseCase::class,
                SendPublicMessageUseCase::class,
                SendPrivateMessageUseCase::class,
                HasPendingPublicKeyChangeUseCase::class,
                AcceptPendingPublicKeyChangeUseCase::class,
                RejectPendingPublicKeyChangeUseCase::class,
                DeleteConversationWithUseCase::class,
                BroadcastLiveAudioChunkUseCase::class,
                BroadcastSeenReceiptUseCase::class,
                ClearSosAlertUseCase::class,
                ObserveConnectionStatusUseCase::class,
                ObserveIsOnlineUseCase::class,
                ObserveCurrentChannelIdUseCase::class,
                ObserveConnectedDevicesUseCase::class,
                ObserveScannedDevicesUseCase::class,
                ObserveIncomingSosAlertUseCase::class,
                ObserveIncomingVoiceMessageUseCase::class,
                ObserveIncomingLiveAudioChunkUseCase::class,
                ObserveKnownNodesUseCase::class,
                ObserveTopologyUseCase::class,
                ObservePublicMessagesUseCase::class,
                ObservePrivateMessagesUseCase::class,
                ObservePeerNamesUseCase::class,
                ObserveBlockedDeviceNamesUseCase::class
            )
        )
    }
}
