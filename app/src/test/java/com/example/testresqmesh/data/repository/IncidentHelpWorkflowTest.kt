package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.DomainEventEntityFakeDao
import com.example.testresqmesh.core.model.IncidentEntityFakeDao
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.data.local.dao.IncidentOfferDao
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IncidentHelpWorkflowTest {
    private val incidentDao = IncidentEntityFakeDao()
    private val eventDao = DomainEventEntityFakeDao()
    private val offerDao = FakeOfferDao()
    private val emitted = mutableListOf<DomainEventEntity>()
    private lateinit var reporter: IncidentHelpWorkflow
    private lateinit var helperB: IncidentHelpWorkflow
    private lateinit var helperC: IncidentHelpWorkflow
    private val incidentId = "INC-HELP-1"

    @Before
    fun setUp() = runTest {
        val incident = IncidentEntity(
            incidentId = incidentId, creatorId = "R", creatorName = "Reporter",
            incidentType = "Medical", severity = "Serious", description = "Need transport",
            areaDescription = "Sector A", status = IncidentState.OPEN.name,
            primaryResponderId = null, primaryResponderName = null,
            version = 1, createdAt = 1, updatedAt = 1,
            workflowVersion = 2, reporterSigningKey = "reporter-key"
        )
        incidentDao.insertOrUpdate(incident)
        fun workflow(userId: String, key: String): IncidentHelpWorkflow = IncidentHelpWorkflow(
            null, incidentDao, offerDao, eventDao, identity(userId), TestSigner(key)
        ) { event, _ -> emitted += event }
        reporter = workflow("R", "reporter-key")
        helperB = workflow("B", "helper-b-key")
        helperC = workflow("C", "helper-c-key")
    }

    @Test
    fun reporterSelectsOneOffer_thenConfirmationAndRevocationConverge() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Can bring a first-aid kit"))
        assertTrue(helperC.offerHelp(incidentId, "Can help with transport"))
        assertEquals(2, offerDao.getForIncident(incidentId).size)
        val offerB = offerDao.getByHelper(incidentId, "helper-b-key")!!
        val offerC = offerDao.getByHelper(incidentId, "helper-c-key")!!

        assertTrue(reporter.selectLead(incidentId, offerB.offerId))
        assertFalse(reporter.selectLead(incidentId, offerC.offerId))
        val pending = incidentDao.getIncidentById(incidentId)!!
        assertEquals(IncidentState.AWAITING_HELPER.name, pending.status)
        assertFalse(helperC.confirmLead(incidentId))
        assertTrue(helperB.confirmLead(incidentId))
        assertEquals(IncidentState.RESPONDING.name, incidentDao.getIncidentById(incidentId)!!.status)

        assertTrue(reporter.revokeLead(incidentId))
        assertTrue(reporter.selectLead(incidentId, offerC.offerId))
        val latest = incidentDao.getIncidentById(incidentId)!!
        val stale = DomainEventEntity(
            "late-b-confirmation", incidentId, "INCIDENT", "INCIDENT_LEAD_CONFIRMED",
            "B", "Helper B", pending.version, 10,
            JSONObject().put("selectionId", pending.selectionId).toString()
        ).let { it.copy(signature = TestSigner("helper-b-key").sign(it)) }
        assertFalse(helperB.apply(stale))
        assertEquals(latest.selectionId, incidentDao.getIncidentById(incidentId)!!.selectionId)
        assertEquals("helper-c-key", incidentDao.getIncidentById(incidentId)!!.selectedHelperKey)
    }

    @Test
    fun withdrawalWhileAwaitingConfirmationNeedsReporterRevocation() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        assertTrue(helperB.withdrawOffer(incidentId))
        val incident = incidentDao.getIncidentById(incidentId)!!
        assertEquals(IncidentState.AWAITING_HELPER.name, incident.status)
        assertTrue(incident.selectionId != null)
        assertFalse(helperB.confirmLead(incidentId))
        assertTrue(reporter.revokeLead(incidentId))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun forgedReporterDecisionIsRejected() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        val forged = DomainEventEntity(
            "forged-select", incidentId, "INCIDENT", "INCIDENT_LEAD_SELECTED",
            "R", "Reporter", 2, 10,
            JSONObject().put("selectionId", "fake").put("offerId", offer.offerId)
                .put("offerRevision", 1).put("helperKey", offer.helperKey).toString()
        ).let { it.copy(signature = TestSigner("helper-b-key").sign(it)) }
        assertFalse(reporter.apply(forged))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun earlyConfirmationWaitsForSelectionAndRejectsStaleSelection() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        val selection = DomainEventEntity(
            "select-b", incidentId, "INCIDENT", "INCIDENT_LEAD_SELECTED", "R", "Reporter", 2, 10,
            JSONObject().put("selectionId", "selection-b").put("offerId", offer.offerId)
                .put("offerRevision", offer.revision).put("helperKey", offer.helperKey).toString()
        ).let { it.copy(signature = TestSigner("reporter-key").sign(it)) }
        val confirmation = DomainEventEntity(
            "confirm-b", incidentId, "INCIDENT", "INCIDENT_LEAD_CONFIRMED", "B", "Helper B", 2, 11,
            JSONObject().put("selectionId", "selection-b").toString()
        ).let { it.copy(signature = TestSigner("helper-b-key").sign(it)) }
        assertFalse(reporter.apply(confirmation))
        assertFalse(eventDao.getEventById(confirmation.eventId)!!.applied)
        assertTrue(reporter.apply(selection))
        assertTrue(reporter.apply(confirmation))
        assertEquals(IncidentState.RESPONDING.name, incidentDao.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun offerRevisionIsIndependentAndSelectionMustNameCurrentRevision() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Can carry supplies"))
        assertTrue(helperB.offerHelp(incidentId, "Can carry supplies and water"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertEquals(2L, offer.revision)
        assertEquals(1, offerDao.getForIncident(incidentId).size)
        assertEquals(1L, incidentDao.getIncidentById(incidentId)!!.version)
        val stale = DomainEventEntity(
            "stale-select", incidentId, "INCIDENT", "INCIDENT_LEAD_SELECTED", "R", "Reporter", 2, 10,
            JSONObject().put("selectionId", "stale").put("offerId", offer.offerId)
                .put("offerRevision", 1).put("helperKey", offer.helperKey).toString()
        ).let { it.copy(signature = TestSigner("reporter-key").sign(it)) }
        assertFalse(reporter.apply(stale))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(incidentId)!!.status)
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
    }

    @Test
    fun cancellationDuringDisconnectionRejectsDelayedConfirmation() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        val selected = incidentDao.getIncidentById(incidentId)!!
        assertTrue(reporter.cancel(incidentId))
        val late = DomainEventEntity(
            "late-after-cancel", incidentId, "INCIDENT", "INCIDENT_LEAD_CONFIRMED", "B", "Helper B",
            selected.version, 12, JSONObject().put("selectionId", selected.selectionId).toString()
        ).let { it.copy(signature = TestSigner("helper-b-key").sign(it)) }
        assertFalse(reporter.apply(late))
        assertEquals(IncidentState.CANCELLED.name, incidentDao.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun observeAllActiveOffers_filtersWithdrawnOffers() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Active Offer B"))
        assertTrue(helperC.offerHelp(incidentId, "Active Offer C"))

        var active = offerDao.observeAllActiveOffers().first()
        assertEquals(2, active.size)

        assertTrue(helperC.withdrawOffer(incidentId))
        active = offerDao.observeAllActiveOffers().first()
        assertEquals(1, active.size)
        assertEquals("Active Offer B", active.first().note)
    }

    private fun identity(id: String): IdentityProvider = object : IdentityProvider {
        private val user = UserEntity(id, "NODE-$id", "Helper $id", null, 1)
        override suspend fun getOrCreateUser(displayName: String?): UserEntity = user
        override fun observeUser(): Flow<UserEntity?> = flowOf(user)
        override suspend fun getUserId(): String = id
        override fun getDeviceId(): String = user.deviceId
    }

    private class TestSigner(override val publicKey: String) : IncidentEventSigning {
        override fun sign(event: DomainEventEntity): String = "$publicKey|${event.eventId}|${event.payloadJson}"
        override fun verify(event: DomainEventEntity, publicKey: String): Boolean =
            event.signature == "$publicKey|${event.eventId}|${event.payloadJson}"
    }

    private class FakeOfferDao : IncidentOfferDao {
        private val rows = mutableMapOf<String, IncidentOfferEntity>()
        private val flow = MutableStateFlow<List<IncidentOfferEntity>>(emptyList())
        override fun observeForIncident(incidentId: String): Flow<List<IncidentOfferEntity>> =
            flow.map { it.filter { offer -> offer.incidentId == incidentId } }
        override fun observeAllActiveOffers(): Flow<List<IncidentOfferEntity>> =
            flow.map { it.filter { offer -> !offer.withdrawn } }
        override suspend fun getForIncident(incidentId: String): List<IncidentOfferEntity> =
            rows.values.filter { it.incidentId == incidentId }
        override suspend fun getById(offerId: String): IncidentOfferEntity? = rows[offerId]
        override suspend fun getByHelper(incidentId: String, helperKey: String): IncidentOfferEntity? =
            rows.values.firstOrNull { it.incidentId == incidentId && it.helperKey == helperKey }
        override suspend fun insertOffer(offerId: String, incidentId: String, helperKey: String,
            helperNodeId: String, helperName: String, note: String, revision: Long,
            withdrawn: Boolean, updatedAt: Long): Long {
            rows[offerId] = IncidentOfferEntity(offerId, incidentId, helperKey, helperNodeId,
                helperName, note, revision, withdrawn, updatedAt)
            flow.value = rows.values.toList()
            return 1
        }
    }
}
