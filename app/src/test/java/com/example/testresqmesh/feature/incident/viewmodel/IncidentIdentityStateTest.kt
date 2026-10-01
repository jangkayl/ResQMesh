package com.example.testresqmesh.feature.incident.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.model.DomainEventEntityFakeDao
import com.example.testresqmesh.core.model.IncidentEntityFakeDao
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentEventSigning
import com.example.testresqmesh.data.repository.IncidentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentIdentityStateTest {
    private val user = MutableStateFlow(UserEntity("r", "node", "Original", null, 1))
    private val identity = object : IdentityProvider {
        override suspend fun getOrCreateUser(displayName: String?) = user.value
        override fun observeUser() = user
        override suspend fun getUserId() = user.value.userId
        override fun getDeviceId() = user.value.deviceId
    }
    private val incident = IncidentEntity("i", "r", "Original", "Medical", "Serious", "Need help", "School",
        status = "OPEN", primaryResponderId = null, primaryResponderName = null, version = 1,
        createdAt = 1, updatedAt = 1, workflowVersion = 2, reporterSigningKey = "rk")

    @Test fun renameAndReturningToIncidentKeepReactiveReporterControlsAndMembership() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dao = IncidentEntityFakeDao()
        dao.insertOrUpdate(incident)
        val vm = IncidentViewModel(repository(dao, signer(), this), identity)
        try {
            assertTrue(vm.identityState.value.loading)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.filteredIncidents.collect {} }
            vm.setDestination(IncidentDestination.MY_ACTIVITY)
            vm.selectIncident(incident)
            runCurrent()
            assertEquals(listOf(incident), vm.filteredIncidents.value)
            assertTrue(presentation(vm).isReporter)
            user.value = user.value.copy(displayName = "New name")
            runCurrent()
            assertEquals("New name", vm.identityState.value.user!!.displayName)
            assertTrue(presentation(vm).isReporter)
            assertTrue(IncidentAction.CANCEL in presentation(vm).secondary)
            assertEquals(listOf(incident), vm.filteredIncidents.value)
            vm.selectIncident(null)
            vm.selectIncident(incident)
            vm.retryIdentity()
            runCurrent()
            assertTrue(presentation(vm).isReporter)
            assertEquals("Original", vm.selectedIncident.value!!.creatorName)
        } finally {
            backgroundScope.cancel()
            vm.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun signingFailureShowsErrorAndRetryRestoresControlsWithoutIdentityChange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dao = IncidentEntityFakeDao()
        dao.insertOrUpdate(incident)
        var available = false
        val signing = object : IncidentEventSigning {
            override val publicKey: String get() { check(available); return "rk" }
            override fun sign(event: DomainEventEntity) = "signature"
            override fun verify(event: DomainEventEntity, publicKey: String) = true
        }
        val vm = IncidentViewModel(repository(dao, signing, this), identity)
        try {
            runCurrent()
            assertFalse(vm.identityState.value.loading)
            assertNotNull(vm.identityState.value.error)
            assertNull(presentation(vm).primary)
            assertFalse(presentation(vm).isReporter)
            available = true
            vm.retryIdentity()
            runCurrent()
            assertNull(vm.identityState.value.error)
            assertEquals("r", vm.identityState.value.user!!.userId)
            assertTrue(presentation(vm).isReporter)
            assertTrue(IncidentAction.CANCEL in presentation(vm).secondary)
        } finally {
            backgroundScope.cancel()
            vm.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun presentation(vm: IncidentViewModel): IncidentPresentation {
        val state = vm.identityState.value
        return incidentPresentation(incident, emptyList(), state.user?.userId, state.signingKey,
            identityLoading = state.loading, identityError = state.error)
    }

    private fun signer() = object : IncidentEventSigning {
        override val publicKey = "rk"
        override fun sign(event: DomainEventEntity) = "signature"
        override fun verify(event: DomainEventEntity, publicKey: String) = true
    }

    private fun repository(dao: IncidentEntityFakeDao, signing: IncidentEventSigning, scope: TestScope): IncidentRepository {
        val gateway = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)) { _, _, _ -> null } as MeshNetworkGateway
        return IncidentRepository(dao, DomainEventEntityFakeDao(), identity, gateway, scope.backgroundScope,
            eventSigning = signing)
    }
}
