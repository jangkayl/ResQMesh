package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.DomainEventEntityFakeDao
import com.example.testresqmesh.core.model.EntityType
import com.example.testresqmesh.core.model.EventType
import com.example.testresqmesh.core.model.IncidentEntityFakeDao
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.core.network.MeshPayload
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentStateMachineAndDeduplicationTest {

    private val incidentDao = IncidentEntityFakeDao()
    private val domainEventDao = DomainEventEntityFakeDao()
    private val testScope = TestScope()
    private lateinit var fakeNetworkGateway: MeshNetworkGateway
    private lateinit var fakeIdentityManager: IdentityProvider
    private lateinit var incidentRepository: IncidentRepository

    @Before
    fun setup() {
        val dummyUser = UserEntity(
            userId = "USR-ALPHA1",
            deviceId = "DEV-NODE1",
            displayName = "Alpha Responder",
            publicKey = null,
            createdAt = 1000L
        )

        fakeNetworkGateway = Proxy.newProxyInstance(
            MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getMyDeviceName" -> "Alpha [TEAM]#NODE1"
                "getMyNodeId" -> "NODE1"
                "currentMeshTtl" -> 10
                "broadcastPriorityPayload" -> Unit
                "broadcastPayload" -> Unit
                else -> null
            }
        } as MeshNetworkGateway

        fakeIdentityManager = object : IdentityProvider {
            override suspend fun getOrCreateUser(displayName: String?): UserEntity = dummyUser
            override fun observeUser(): Flow<UserEntity?> = flowOf(dummyUser)
            override suspend fun getUserId(): String = dummyUser.userId
            override fun getDeviceId(): String = dummyUser.deviceId
        }

        incidentRepository = IncidentRepository(
            incidentDao = incidentDao,
            domainEventDao = domainEventDao,
            identityManager = fakeIdentityManager,
            networkGateway = fakeNetworkGateway,
            repositoryScope = testScope
        )
    }

    @Test
    fun createIncident_initializesInOpenStateWithVersion1() = testScope.runTest {
        val incident = incidentRepository.createIncident(
            incidentType = "Medical",
            severity = "Critical",
            description = "Patient injured",
            areaDescription = "Sector 4"
        )

        assertEquals("OPEN", incident.status)
        assertEquals(1L, incident.version)
        assertEquals("USR-ALPHA1", incident.creatorId)

        val stored = incidentDao.getIncidentById(incident.incidentId)
        assertNotNull(stored)
        assertEquals("Medical", stored?.incidentType)

        val events = domainEventDao.getEventsForEntity(incident.incidentId)
        assertEquals(1, events.size)
        assertEquals("INCIDENT_CREATED", events[0].eventType)
    }

    @Test
    fun deduplication_ignoresDuplicateEventIds() = testScope.runTest {
        val event = DomainEventEntity(
            eventId = "EVT-DUP-01",
            entityId = "INC-100",
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CREATED.name,
            actorId = "USR-REMOTE",
            actorName = "Remote Node",
            logicalVersion = 1L,
            timestamp = 2000L,
            payloadJson = JSONObject().apply {
                put("incidentType", "Fire")
                put("severity", "Critical")
            }.toString(),
            applied = true
        )

        // First application should succeed
        val firstApplied = incidentRepository.applyIncomingEvent(event)
        assertTrue("First receipt must apply", firstApplied)

        // Second application of the exact same eventId should be rejected by deduplication
        val secondApplied = incidentRepository.applyIncomingEvent(event)
        assertFalse("Duplicate receipt must be dropped without re-applying", secondApplied)

        // Verify entity state exists only once
        val stored = incidentDao.getIncidentById("INC-100")
        assertNotNull(stored)
        assertEquals("Fire", stored?.incidentType)
    }

    @Test
    fun creatorCannotAcknowledgeOrClaimOwnIncident() = testScope.runTest {
        val inc = incidentRepository.createIncident("Trapped", "Critical", "Trapped in room", "Area A")
        val id = inc.incidentId

        assertFalse(incidentRepository.acknowledgeIncident(id))
        assertFalse(incidentRepository.assignIncident(id))
        assertFalse(incidentRepository.startResponding(id))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(id)?.status)
    }

    @Test
    fun invalidStateTransitions_areRejected() = testScope.runTest {
        val inc = incidentRepository.createIncident("Flood", "Moderate", "Rising water", "Area B")
        val id = inc.incidentId

        // Cannot jump directly from OPEN to RESOLVED
        assertFalse(incidentRepository.resolveIncident(id))
        assertEquals("OPEN", incidentDao.getIncidentById(id)?.status)

        // Cannot start responding before assigned
        assertFalse(incidentRepository.startResponding(id))
        assertEquals("OPEN", incidentDao.getIncidentById(id)?.status)
    }

    @Test
    fun remoteNonCreatorCannotCancelIncident() = testScope.runTest {
        val incident = incidentRepository.createIncident("Fire", "Critical", "Warehouse", "Area C")
        val cancellation = DomainEventEntity(
            eventId = "EVT-UNAUTHORIZED-CANCEL",
            entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CANCELLED.name,
            actorId = "USR-OTHER",
            actorName = "Other",
            logicalVersion = 2L,
            timestamp = 2000L,
            payloadJson = "{}"
        )

        assertFalse(incidentRepository.applyIncomingEvent(cancellation))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(incident.incidentId)?.status)
        assertFalse(domainEventDao.getEventById(cancellation.eventId)?.applied ?: true)
    }

    @Test
    fun remoteNonResponderCannotStartResponse() = testScope.runTest {
        val incident = incidentRepository.createIncident("Flood", "High", "Water rising", "Area D")
        val assignment = DomainEventEntity(
            eventId = "EVT-REMOTE-ASSIGN", entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name, eventType = EventType.INCIDENT_ASSIGNED.name,
            actorId = "USR-BOB", actorName = "Bob", logicalVersion = 2L, timestamp = 2000L,
            payloadJson = JSONObject().put("responderId", "USR-BOB").put("responderName", "Bob").toString()
        )
        assertTrue(incidentRepository.applyIncomingEvent(assignment))
        val start = DomainEventEntity(
            eventId = "EVT-UNAUTHORIZED-START",
            entityId = incident.incidentId,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_RESPONSE_STARTED.name,
            actorId = "USR-OTHER",
            actorName = "Other",
            logicalVersion = 3L,
            timestamp = 3000L,
            payloadJson = "{}"
        )

        assertFalse(incidentRepository.applyIncomingEvent(start))
        assertEquals(IncidentState.ASSIGNED.name, incidentDao.getIncidentById(incident.incidentId)?.status)
    }

    @Test
    fun simultaneousAssignmentConflict_deterministicWinnerWins() = testScope.runTest {
        // Create initial incident
        val inc = incidentRepository.createIncident("Injury", "Serious", "Broken leg", "Sector 9")
        val id = inc.incidentId

        // Responder B assigned at t=3000
        val assignB = DomainEventEntity(
            eventId = "EVT-B",
            entityId = id,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNED.name,
            actorId = "USR-BOB",
            actorName = "Bob",
            logicalVersion = 2L,
            timestamp = 3000L,
            payloadJson = JSONObject().apply {
                put("responderId", "USR-BOB")
                put("responderName", "Bob")
            }.toString(),
            applied = true
        )
        incidentRepository.applyIncomingEvent(assignB)
        assertEquals("USR-BOB", incidentDao.getIncidentById(id)?.primaryResponderId)

        // Competing Responder C assigned EARLIER at t=2500 during network partition
        val assignC = DomainEventEntity(
            eventId = "EVT-C",
            entityId = id,
            entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_ASSIGNED.name,
            actorId = "USR-CHARLIE",
            actorName = "Charlie",
            logicalVersion = 2L,
            timestamp = 2500L,
            payloadJson = JSONObject().apply {
                put("responderId", "USR-CHARLIE")
                put("responderName", "Charlie")
            }.toString(),
            applied = true
        )

        // Charlie's earlier event should win conflict resolution
        val appliedCharlie = incidentRepository.applyIncomingEvent(assignC)
        assertTrue("Earlier assignment event should win conflict", appliedCharlie)
        assertEquals("USR-CHARLIE", incidentDao.getIncidentById(id)?.primaryResponderId)
    }

    @Test
    fun remoteCreatorCannotAcknowledgeOwnIncident() = testScope.runTest {
        val created = DomainEventEntity(
            eventId = "EVT-CREATE-REMOTE", entityId = "INC-REMOTE", entityType = EntityType.INCIDENT.name,
            eventType = EventType.INCIDENT_CREATED.name, actorId = "USR-REMOTE", actorName = "Remote",
            logicalVersion = 1L, timestamp = 1000L, payloadJson = "{}"
        )
        assertTrue(incidentRepository.applyIncomingEvent(created))
        val ownAck = created.copy(eventId = "EVT-REMOTE-ACK", eventType = EventType.INCIDENT_ACKNOWLEDGED.name, logicalVersion = 2L)
        assertFalse(incidentRepository.applyIncomingEvent(ownAck))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById("INC-REMOTE")?.status)
    }

    @Test
    fun nonCreatorResponderCanCompleteLifecycle() = testScope.runTest {
        val incident = incidentRepository.createIncident("Fire", "Critical", "Warehouse", "Area C")
        val id = incident.incidentId
        val ack = DomainEventEntity("EVT-ACK-B", id, EntityType.INCIDENT.name, EventType.INCIDENT_ACKNOWLEDGED.name, "USR-BOB", "Bob", 2L, 2000L, "{}")
        val assign = DomainEventEntity("EVT-ASSIGN-B", id, EntityType.INCIDENT.name, EventType.INCIDENT_ASSIGNED.name, "USR-BOB", "Bob", 3L, 3000L, JSONObject().put("responderId", "USR-BOB").put("responderName", "Bob").toString())
        val start = DomainEventEntity("EVT-START-B", id, EntityType.INCIDENT.name, EventType.INCIDENT_RESPONSE_STARTED.name, "USR-BOB", "Bob", 4L, 4000L, "{}")
        val resolve = DomainEventEntity("EVT-RESOLVE-B", id, EntityType.INCIDENT.name, EventType.INCIDENT_RESOLVED.name, "USR-BOB", "Bob", 5L, 5000L, "{}")

        assertTrue(incidentRepository.applyIncomingEvent(ack))
        assertTrue(incidentRepository.applyIncomingEvent(assign))
        assertTrue(incidentRepository.applyIncomingEvent(start))
        assertTrue(incidentRepository.applyIncomingEvent(resolve))
        assertEquals(IncidentState.RESOLVED.name, incidentDao.getIncidentById(id)?.status)
    }

    @Test
    fun incidentLocationRoundTripsThroughCreationPayload() = testScope.runTest {
        val incident = incidentRepository.createIncident(
            "Medical", "Critical", "Need assistance", "Gate 2",
            latitude = 10.3157, longitude = 123.8854, locationCapturedAt = 1234L, locationAccuracyMeters = 12f
        )
        assertEquals(10.3157, incident.latitude)
        assertEquals(123.8854, incident.longitude)
        val payload = JSONObject(domainEventDao.getEventsForEntity(incident.incidentId).single().payloadJson)
        assertEquals(10.3157, payload.getDouble("latitude"), 0.0)
        assertEquals(12.0, payload.getDouble("locationAccuracyMeters"), 0.0)
    }
}
