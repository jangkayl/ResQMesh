package com.example.testresqmesh.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.data.local.dao.DomainEventDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

/** Real Room coverage: a failed event write must roll back both withdrawal and selection cleanup. */
class IncidentWithdrawalTransactionTest {
    private lateinit var database: AppDatabase
    private val id = "withdrawal-transaction"

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database.incidentDao().insertOrUpdate(IncidentEntity(id, "R", "Reporter", "Medical", "Serious",
            "Need help", "School", status = "OPEN", primaryResponderId = null, primaryResponderName = null,
            version = 1, createdAt = 1, updatedAt = 1, workflowVersion = 2, reporterSigningKey = "rk"))
    }

    @After fun tearDown() { database.close() }

    @Test fun confirmedWithdrawalPersistsOfferEventAndClearedSelection() = runBlocking {
        val reporter = workflow("R", "rk")
        val helper = workflow("B", "bk")
        assertTrue(helper.offerHelp(id, "Nearby"))
        val offer = database.incidentOfferDao().getByHelper(id, "bk")!!
        assertTrue(reporter.selectLead(id, offer.offerId))
        assertTrue(helper.confirmLead(id))
        assertTrue(helper.withdrawOffer(id))
        val incident = database.incidentDao().getIncidentById(id)!!
        assertEquals("OPEN", incident.status)
        assertEquals(2L, incident.version)
        assertNull(incident.selectionId)
        assertNull(incident.selectionOfferId)
        assertNull(incident.selectionOfferRevision)
        assertNull(incident.selectedHelperKey)
        assertNull(incident.selectionConfirmedAt)
        assertNull(incident.primaryResponderId)
        assertNull(incident.primaryResponderName)
        assertTrue(database.incidentOfferDao().getById(offer.offerId)!!.withdrawn)
        assertTrue(database.domainEventDao().getEventsForEntity(id)
            .single { it.eventType == "INCIDENT_OFFER_WITHDRAWN" }.applied)
    }

    @Test fun eventPersistenceFailureRollsBackOfferAndSelectionTogether() = runBlocking {
        val reporter = workflow("R", "rk")
        val helper = workflow("B", "bk")
        assertTrue(helper.offerHelp(id, "Nearby"))
        val offer = database.incidentOfferDao().getByHelper(id, "bk")!!
        assertTrue(reporter.selectLead(id, offer.offerId))
        assertTrue(helper.confirmLead(id))
        val incident = database.incidentDao().getIncidentById(id)!!
        val delegate = database.domainEventDao()
        val failingEvents = object : DomainEventDao by delegate {
            override suspend fun insertEvent(event: DomainEventEntity): Long {
                if (event.eventType == "INCIDENT_OFFER_WITHDRAWN") error("Injected event write failure")
                return delegate.insertEvent(event)
            }
        }
        val result = runCatching { workflow("B", "bk", failingEvents).withdrawOffer(id) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(offer, database.incidentOfferDao().getById(offer.offerId))
        assertEquals(incident, database.incidentDao().getIncidentById(id))
        assertTrue(delegate.getEventsForEntity(id).none { it.eventType == "INCIDENT_OFFER_WITHDRAWN" })
    }

    @Test fun offlineEditPersistenceFailureAlsoRollsBackSelectedCommitment() = runBlocking {
        val reporter = workflow("R", "rk")
        val helper = workflow("B", "bk")
        assertTrue(helper.offerHelp(id, "Nearby"))
        val offer = database.incidentOfferDao().getByHelper(id, "bk")!!
        assertTrue(reporter.selectLead(id, offer.offerId))
        assertTrue(helper.confirmLead(id))
        val incident = database.incidentDao().getIncidentById(id)!!
        val delegate = database.domainEventDao()
        val first = delegate.getEventsForEntity(id).single { it.eventType == "INCIDENT_OFFER_UPDATED" }
        val edit = first.copy(eventId = "offline-edit", logicalVersion = 2,
            payloadJson = JSONObject(first.payloadJson).put("revision", 2).put("note", "Changed offline").toString())
            .let { it.copy(signature = "bk|${it.eventId}|${it.payloadJson}") }
        val failingEvents = object : DomainEventDao by delegate {
            override suspend fun insertEvent(event: DomainEventEntity): Long {
                if (event.eventId == edit.eventId) error("Injected event write failure")
                return delegate.insertEvent(event)
            }
        }
        assertTrue(runCatching { workflow("B", "bk", failingEvents).apply(edit) }.isFailure)
        assertEquals(offer, database.incidentOfferDao().getById(offer.offerId))
        assertEquals(incident, database.incidentDao().getIncidentById(id))
        assertNull(delegate.getEventById(edit.eventId))
    }

    private fun workflow(userId: String, key: String, events: DomainEventDao = database.domainEventDao()): IncidentHelpWorkflow {
        val user = UserEntity(userId, "node-$userId", userId, null, 1)
        val identity = object : IdentityProvider {
            override suspend fun getOrCreateUser(displayName: String?) = user
            override fun observeUser() = flowOf(user)
            override suspend fun getUserId() = userId
            override fun getDeviceId() = user.deviceId
        }
        val signer = object : IncidentEventSigning {
            override val publicKey = key
            override fun sign(event: DomainEventEntity) = "$key|${event.eventId}|${event.payloadJson}"
            override fun verify(event: DomainEventEntity, publicKey: String) =
                event.signature == "$publicKey|${event.eventId}|${event.payloadJson}"
        }
        return IncidentHelpWorkflow(database, database.incidentDao(), database.incidentOfferDao(), events,
            identity, signer) { _, _ -> }
    }
}
