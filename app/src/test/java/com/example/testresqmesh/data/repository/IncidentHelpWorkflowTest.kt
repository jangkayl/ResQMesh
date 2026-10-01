package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.DomainEventEntityFakeDao
import com.example.testresqmesh.core.model.IncidentEntityFakeDao
import com.example.testresqmesh.core.model.IncidentState
import com.example.testresqmesh.core.network.MeshNetworkGateway
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class IncidentHelpWorkflowTest {
    private val incidentDao = IncidentEntityFakeDao()
    private val eventDao = DomainEventEntityFakeDao()
    private val offerDao = FakeOfferDao()
    private val emitted = mutableListOf<DomainEventEntity>()
    private lateinit var reporter: IncidentHelpWorkflow
    private lateinit var helperB: IncidentHelpWorkflow
    private lateinit var helperC: IncidentHelpWorkflow
    private lateinit var initialIncident: IncidentEntity
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
        initialIncident = incident
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
    fun withdrawalWhileAwaitingConfirmationClearsSelectionAndAllowsAnotherHelper() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        assertTrue(helperB.withdrawOffer(incidentId))
        val incident = incidentDao.getIncidentById(incidentId)!!
        assertCleared(incident)
        assertEquals(2L, incident.version)
        assertFalse(helperB.confirmLead(incidentId))
        assertFalse(reporter.revokeLead(incidentId))
        assertTrue(helperC.offerHelp(incidentId, "Available"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-c-key")!!.offerId))
        assertEquals("helper-c-key", incidentDao.getIncidentById(incidentId)!!.selectedHelperKey)
    }

    @Test
    fun confirmedHelperWithdrawalClearsResponderAndCanOfferAgain() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        val confirmation = emitted.last()
        assertTrue(helperB.withdrawOffer(incidentId))
        val withdrawal = emitted.last()
        val cleared = incidentDao.getIncidentById(incidentId)!!
        assertCleared(cleared)
        assertEquals(2L, cleared.version)
        assertFalse(helperB.withdrawOffer(incidentId))
        assertFalse(reporter.apply(withdrawal))
        val delayed = confirmation.copy(eventId = "late-confirmation", signature = null)
        assertFalse(reporter.apply(delayed.copy(signature = TestSigner("helper-b-key").sign(delayed))))
        assertEquals(cleared, incidentDao.getIncidentById(incidentId))
        assertTrue(helperB.offerHelp(incidentId, "Available again"))
        val renewed = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertEquals(3L, renewed.revision)
        assertCleared(incidentDao.getIncidentById(incidentId)!!)
        assertTrue(reporter.selectLead(incidentId, renewed.offerId))
        assertTrue(helperB.confirmLead(incidentId))
    }

    @Test
    fun unselectedWithdrawalLeavesConfirmedLeadUntouched() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(helperC.offerHelp(incidentId, "Transport"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        val confirmed = incidentDao.getIncidentById(incidentId)!!
        assertTrue(helperC.withdrawOffer(incidentId))
        assertEquals(confirmed, incidentDao.getIncidentById(incidentId))
    }

    @Test
    fun terminalHistoryIsNotReopenedByWithdrawalOrRepair() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        val active = incidentDao.getIncidentById(incidentId)!!
        for (status in listOf("RESOLVED", "CANCELLED")) {
            val terminal = active.copy(status = status)
            incidentDao.insertOrUpdate(terminal)
            assertFalse(helperB.withdrawOffer(incidentId))
            assertFalse(helperB.offerHelp(incidentId, "Cannot edit closed incident"))
            reporter.reconcileWithdrawnSelection(incidentId)
            assertEquals(terminal, incidentDao.getIncidentById(incidentId))
        }
    }

    @Test
    fun existingStuckSelectionsAreRepairedIdempotentlyEvenAfterReoffer() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        val stuck = incidentDao.getIncidentById(incidentId)!!
        assertTrue(helperB.withdrawOffer(incidentId))
        assertTrue(helperB.offerHelp(incidentId, "Available again"))
        incidentDao.insertOrUpdate(stuck)
        reporter.reconcileWithdrawnSelection(incidentId)
        val repaired = incidentDao.getIncidentById(incidentId)!!
        assertCleared(repaired)
        assertEquals(stuck.version, repaired.version)
        reporter.reconcileWithdrawnSelection(incidentId)
        assertEquals(repaired, incidentDao.getIncidentById(incidentId))
    }

    @Test
    fun reorderedIndependentReplicasConvergeThroughLegacyRevocationAndNextSelection() = runTest {
        val replicas = List(3) { replica() }
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offerB = emitted.last()
        assertTrue(helperC.offerHelp(incidentId, "Transport"))
        val offerC = emitted.last()
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selection = emitted.last()
        val selected = incidentDao.getIncidentById(incidentId)!!
        assertTrue(helperB.confirmLead(incidentId))
        val confirmation = emitted.last()
        assertTrue(helperB.withdrawOffer(incidentId))
        val withdrawal = emitted.last()
        val revocation = signedRevocation(selected)
        assertTrue(reporter.apply(revocation))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-c-key")!!.offerId))
        val nextSelection = emitted.last()
        val orders = listOf(
            listOf(offerB, offerC, selection, confirmation, withdrawal, revocation, nextSelection),
            listOf(withdrawal, confirmation, nextSelection, revocation, selection, offerC, offerB),
            listOf(offerB, withdrawal, offerC, revocation, nextSelection, confirmation, selection)
        )
        replicas.zip(orders).forEach { (replica, order) ->
            order.forEach { replica.workflow.apply(it) }
            replica.replay()
            val actual = replica.incidents.getIncidentById(incidentId)!!
            assertEquals(incidentDao.getIncidentById(incidentId), actual)
            assertEquals(4L, actual.version)
            assertEquals("helper-c-key", actual.selectedHelperKey)
            assertEquals(offerDao.getForIncident(incidentId).sortedBy { it.offerId },
                replica.offers.getForIncident(incidentId).sortedBy { it.offerId })
            assertTrue(replica.events.getEventById(selection.eventId)!!.applied)
            assertTrue(replica.events.getEventById(revocation.eventId)!!.applied)
            assertFalse(replica.workflow.apply(withdrawal))
        }
    }

    @Test
    fun withdrawalBeforeOldSelectionDoesNotRestoreHelperAfterReoffer() = runTest {
        val replica = replica()
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = emitted.last()
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selection = emitted.last()
        assertTrue(helperB.withdrawOffer(incidentId))
        val withdrawal = emitted.last()
        assertTrue(helperB.offerHelp(incidentId, "Available again"))
        val reoffer = emitted.last()
        listOf(offer, withdrawal, reoffer, selection).forEach { assertTrue(replica.workflow.apply(it)) }
        assertCleared(replica.incidents.getIncidentById(incidentId)!!)
        assertEquals(2L, replica.incidents.getIncidentById(incidentId)!!.version)
    }

    @Test
    fun reconnectEventPageReplaysWithdrawalBeforeSelectionAndNextReporterDecision() = runTest {
        val replica = replica()
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = emitted.last()
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selection = emitted.last()
        assertTrue(helperB.withdrawOffer(incidentId))
        val withdrawal = emitted.last()
        assertTrue(reporter.cancel(incidentId))
        val cancellation = emitted.last()
        val repository = IncidentRepository(replica.incidents, replica.events, identity("R"), gateway(),
            backgroundScope, offerDao = replica.offers, eventSigning = TestSigner("reporter-key"))
        val page = JSONObject().put("type", "INCIDENT_EVENT_PAGE").put("events", JSONArray().apply {
            listOf(cancellation, withdrawal, selection, offer).forEach { event ->
                put(JSONObject().put("eventId", event.eventId).put("entityId", event.entityId)
                    .put("entityType", event.entityType).put("eventType", event.eventType)
                    .put("actorId", event.actorId).put("actorName", event.actorName)
                    .put("logicalVersion", event.logicalVersion).put("timestamp", event.timestamp)
                    .put("payloadJson", event.payloadJson).put("signature", event.signature))
            }
        }).toString()
        repository.handleSyncResponse("peer", page)
        assertEquals(incidentDao.getIncidentById(incidentId), replica.incidents.getIncidentById(incidentId))
        assertEquals("CANCELLED", replica.incidents.getIncidentById(incidentId)!!.status)
        repository.handleSyncResponse("peer", page)
        assertEquals(3L, replica.incidents.getIncidentById(incidentId)!!.version)
    }

    @Test
    fun closureConvergesForEveryEqualTimestampDeliveryOrder() = runTest {
        val history = withdrawalAndCancellationHistory()
        val expected = freshReplica()
        history.forEach { assertTrue(expected.workflow.apply(it)) }
        for (order in permutations(history)) {
            val target = freshReplica()
            repository(target).handleSyncResponse("peer", eventPage(order))
            assertEquals(expected.incidents.getIncidentById(incidentId), target.incidents.getIncidentById(incidentId))
            assertEquals(expected.offers.getForIncident(incidentId), target.offers.getForIncident(incidentId))
            assertTrue(target.events.getUnappliedForIncident(incidentId).isEmpty())
            repository(target).handleSyncResponse("peer", eventPage(order))
            assertEquals(expected.incidents.getIncidentById(incidentId), target.incidents.getIncidentById(incidentId))
        }
    }

    @Test
    fun closureWaitsAcrossPagesAndRepositoryRestartDespiteReversedClocks() = runTest {
        val history = withdrawalAndCancellationHistory().mapIndexed { index, event ->
            signed(event.copy(timestamp = 100L - index))
        }
        val target = freshReplica()
        val (offer, selection, withdrawal, cancellation) = history
        repository(target).handleSyncResponse("peer", eventPage(listOf(cancellation, selection, offer)))
        assertEquals(IncidentState.AWAITING_HELPER.name, target.incidents.getIncidentById(incidentId)!!.status)
        assertFalse(target.events.getEventById(cancellation.eventId)!!.applied)
        // A fresh repository uses only persisted events/projections, not an in-memory queue.
        repository(target).handleSyncResponse("peer", eventPage(listOf(withdrawal)))
        val actual = target.incidents.getIncidentById(incidentId)!!
        assertEquals(IncidentState.CANCELLED.name, actual.status)
        assertNull(actual.selectionId)
        assertNull(actual.primaryResponderName)
        assertEquals(3L, actual.version)
        assertTrue(target.offers.getForIncident(incidentId).single().withdrawn)
        assertTrue(target.events.getUnappliedForIncident(incidentId).isEmpty())
    }

    @Test
    fun forgedWithdrawalCannotSatisfyClosureDependency() = runTest {
        val (offer, selection, withdrawal, cancellation) = withdrawalAndCancellationHistory()
        val target = freshReplica()
        repository(target).handleSyncResponse("peer", eventPage(listOf(offer, selection, cancellation,
            withdrawal.copy(signature = "forged"))))
        assertEquals(IncidentState.AWAITING_HELPER.name, target.incidents.getIncidentById(incidentId)!!.status)
        assertFalse(target.events.getEventById(cancellation.eventId)!!.applied)
        repository(target).handleSyncResponse("peer", eventPage(listOf(withdrawal)))
        assertEquals(IncidentState.CANCELLED.name, target.incidents.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun legacyClosureDrainsSamePageWithdrawalBeforeCancellation() = runTest {
        val history = withdrawalAndCancellationHistory().map { event ->
            if (event.eventType == "INCIDENT_CANCELLED") signed(event.copy(payloadJson = "{}")) else event
        }
        val target = freshReplica()
        repository(target).handleSyncResponse("peer", eventPage(history.reversed()))
        val actual = target.incidents.getIncidentById(incidentId)!!
        assertEquals(IncidentState.CANCELLED.name, actual.status)
        assertNull(actual.selectionId)
        assertNull(actual.primaryResponderName)
        assertTrue(target.offers.getForIncident(incidentId).single().withdrawn)
    }

    @Test
    fun resolutionWaitsForConfirmationAndRetainsConfirmedHelperHistory() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        assertTrue(reporter.resolve(incidentId))
        val (offer, selection, confirmation, resolution) = emitted.toList()
        val target = freshReplica()
        repository(target).handleSyncResponse("peer", eventPage(listOf(resolution, selection, offer)))
        assertEquals(IncidentState.AWAITING_HELPER.name, target.incidents.getIncidentById(incidentId)!!.status)
        repository(target).handleSyncResponse("peer", eventPage(listOf(confirmation)))
        assertEquals(incidentDao.getIncidentById(incidentId), target.incidents.getIncidentById(incidentId))
        assertEquals(IncidentState.RESOLVED.name, target.incidents.getIncidentById(incidentId)!!.status)
        assertFalse(target.workflow.apply(signed(confirmation.copy(eventId = "late-confirmation"))))
    }

    @Test
    fun closureRejectsForeignIncidentDependenciesAndUnknownProtocol() = runTest {
        val (offer, selection, withdrawal, cancellation) = withdrawalAndCancellationHistory()
        val target = freshReplica()
        listOf(offer, selection, withdrawal).forEach { assertTrue(target.workflow.apply(it)) }
        val foreign = offer.copy(eventId = "foreign", entityId = "another-incident")
        target.events.insertEvent(foreign)
        val payload = JSONObject(cancellation.payloadJson).put("closureDependencies", JSONArray(listOf("foreign")))
        assertFalse(target.workflow.apply(signed(cancellation.copy(payloadJson = payload.toString()))))
        payload.put("closureProtocol", 2).put("closureDependencies", JSONArray())
        assertFalse(target.workflow.apply(signed(cancellation.copy(payloadJson = payload.toString()))))
        assertEquals(IncidentState.OPEN.name, target.incidents.getIncidentById(incidentId)!!.status)
    }

    @Test
    fun closureRetainsObservedConfirmedHistoryDespiteNewerOfflineOffer() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        assertTrue(reporter.cancel(incidentId))
        val (offer, selection, confirmation, cancellation) = emitted.toList()
        val target = freshReplica()
        listOf(offer, selection, confirmation).forEach { assertTrue(target.workflow.apply(it)) }
        val update = signed(offer.copy(eventId = "offline-edit", logicalVersion = 2,
            payloadJson = JSONObject(offer.payloadJson).put("revision", 2).put("note", "Edited offline").toString()))
        assertTrue(target.workflow.apply(update))
        assertNull(target.incidents.getIncidentById(incidentId)!!.selectionId)
        assertTrue(target.workflow.apply(cancellation))
        assertEquals(incidentDao.getIncidentById(incidentId), target.incidents.getIncidentById(incidentId))
        assertFalse(target.workflow.apply(signed(confirmation.copy(eventId = "late-confirmation"))))
    }

    private suspend fun withdrawalAndCancellationHistory(): List<DomainEventEntity> {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        assertTrue(helperB.withdrawOffer(incidentId))
        assertTrue(reporter.cancel(incidentId))
        // Force cancellation to sort before withdrawal; the clock supplies no causal order.
        val ids = listOf("b-offer", "c-selection", "z-withdrawal", "a-cancellation")
        val mapping = emitted.mapIndexed { index, event -> event.eventId to ids[index] }.toMap()
        return emitted.map { event ->
            val payload = JSONObject(event.payloadJson)
            payload.optJSONArray("closureDependencies")?.let { refs ->
                payload.put("closureDependencies", JSONArray((0 until refs.length()).map { mapping.getValue(refs.getString(it)) }))
            }
            signed(event.copy(eventId = mapping.getValue(event.eventId), timestamp = 10, payloadJson = payload.toString()))
        }
    }

    private fun signed(event: DomainEventEntity): DomainEventEntity {
        val key = when (event.actorId) { "R" -> "reporter-key"; "B" -> "helper-b-key"; else -> "helper-c-key" }
        return event.copy(signature = TestSigner(key).sign(event))
    }

    private fun TestScope.repository(target: Replica) = IncidentRepository(target.incidents, target.events, identity("R"), gateway(),
        backgroundScope,
        offerDao = target.offers, eventSigning = TestSigner("reporter-key"))

    private fun eventPage(history: List<DomainEventEntity>) = JSONObject().put("type", "INCIDENT_EVENT_PAGE")
        .put("events", JSONArray(history.map { event -> JSONObject().put("eventId", event.eventId)
            .put("entityId", event.entityId).put("entityType", event.entityType).put("eventType", event.eventType)
            .put("actorId", event.actorId).put("actorName", event.actorName).put("logicalVersion", event.logicalVersion)
            .put("timestamp", event.timestamp).put("payloadJson", event.payloadJson).put("signature", event.signature) })).toString()

    private fun <T> permutations(items: List<T>): List<List<T>> = if (items.isEmpty()) listOf(emptyList())
        else items.flatMap { item -> permutations(items - item).map { listOf(item) + it } }

    @Test
    fun revocationNoOpNeedsMatchingSignedHistoryAndDoesNotClearNewSelection() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(helperC.offerHelp(incidentId, "Transport"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selected = incidentDao.getIncidentById(incidentId)!!
        assertTrue(helperB.withdrawOffer(incidentId))
        val wrongTarget = signedRevocation(selected, selectionId = "unknown")
        assertFalse(reporter.apply(wrongTarget))
        val forged = signedRevocation(selected).copy(signature = "forged")
        assertFalse(reporter.apply(forged))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-c-key")!!.offerId))
        val current = incidentDao.getIncidentById(incidentId)!!
        assertTrue(reporter.apply(signedRevocation(selected, version = current.version + 1)))
        assertEquals(current.selectionId, incidentDao.getIncidentById(incidentId)!!.selectionId)
        assertEquals("helper-c-key", incidentDao.getIncidentById(incidentId)!!.selectedHelperKey)
    }

    @Test
    fun forgedWithdrawalCannotClearSelection() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        val selected = incidentDao.getIncidentById(incidentId)!!
        val event = DomainEventEntity("forged-withdraw", incidentId, "INCIDENT", "INCIDENT_OFFER_WITHDRAWN",
            "B", "Helper B", 2, 10, JSONObject().put("offerId", offer.offerId)
                .put("helperKey", offer.helperKey).put("revision", 2).toString())
        assertFalse(reporter.apply(event.copy(signature = TestSigner("helper-c-key").sign(event))))
        assertEquals(selected, incidentDao.getIncidentById(incidentId))
    }

    private fun assertCleared(incident: IncidentEntity) {
        assertEquals(IncidentState.OPEN.name, incident.status)
        assertNull(incident.selectionId)
        assertNull(incident.selectionOfferId)
        assertNull(incident.selectionOfferRevision)
        assertNull(incident.selectedHelperKey)
        assertNull(incident.selectionConfirmedAt)
        assertNull(incident.primaryResponderId)
        assertNull(incident.primaryResponderName)
    }

    @Test fun selectedOffersRejectLocalEditingUntilRemovedOrWithdrawn() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Initial offer"))
        val offer = offerDao.getByHelper(incidentId, "helper-b-key")!!
        assertTrue(helperB.offerHelp(incidentId, "Revised before selection"))
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        val selectedOffer = offerDao.getById(offer.offerId)!!
        assertFalse(helperB.offerHelp(incidentId, "Not allowed while selected"))
        assertEquals(selectedOffer, offerDao.getById(offer.offerId))
        assertTrue(helperB.confirmLead(incidentId))
        assertFalse(helperB.offerHelp(incidentId, "Not allowed after confirmation"))
        assertEquals(selectedOffer, offerDao.getById(offer.offerId))
        assertTrue(reporter.revokeLead(incidentId))
        assertTrue(helperB.offerHelp(incidentId, "Can edit after removal"))
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
        assertTrue(helperB.withdrawOffer(incidentId))
        assertTrue(helperB.offerHelp(incidentId, "Fresh offer"))
        assertTrue(reporter.selectLead(incidentId, offer.offerId))
    }

    @Test fun signedOfflineEditAndSelectionConvergeInEitherOrder() = runTest {
        val replicas = List(3) { replica() }
        assertTrue(helperB.offerHelp(incidentId, "Initial offer"))
        val initialOffer = emitted.last()
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selection = emitted.last()
        assertTrue(helperB.confirmLead(incidentId))
        val confirmation = emitted.last()
        val edit = initialOffer.copy(eventId = "offline-edit", logicalVersion = 2,
            timestamp = initialOffer.timestamp + 20, payloadJson = JSONObject(initialOffer.payloadJson)
                .put("revision", 2).put("note", "Edited before receiving selection").toString(), signature = null)
            .let { it.copy(signature = TestSigner("helper-b-key").sign(it)) }
        assertTrue(reporter.apply(edit))
        assertCleared(incidentDao.getIncidentById(incidentId)!!)
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val nextSelection = emitted.last()
        val orders = listOf(
            listOf(initialOffer, selection, confirmation, edit, nextSelection),
            listOf(initialOffer, edit, selection, confirmation, nextSelection),
            listOf(nextSelection, confirmation, edit, selection, initialOffer)
        )
        replicas.zip(orders).forEach { (replica, order) ->
            order.forEach { replica.workflow.apply(it) }
            replica.replay()
            val actual = replica.incidents.getIncidentById(incidentId)!!
            assertEquals(incidentDao.getIncidentById(incidentId), actual)
            assertEquals(3L, actual.version)
            assertEquals(2L, actual.selectionOfferRevision)
            assertTrue(replica.events.getEventById(selection.eventId)!!.applied)
            assertFalse(replica.workflow.apply(edit))
        }
    }

    @Test fun renamedReporterKeepsActionsAndSignedHistoryUnchanged() = runTest {
        val user = MutableStateFlow(UserEntity("R", "NODE-R", "Original name", null, 1))
        val identity = object : IdentityProvider {
            override suspend fun getOrCreateUser(displayName: String?): UserEntity {
                if (displayName != null) user.value = user.value.copy(displayName = displayName)
                return user.value
            }
            override fun observeUser() = user
            override suspend fun getUserId() = user.value.userId
            override fun getDeviceId() = "NODE-R"
        }
        val renamedReporter = IncidentHelpWorkflow(null, incidentDao, offerDao, eventDao, identity,
            TestSigner("reporter-key")) { event, _ -> emitted += event }
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        identity.getOrCreateUser("Renamed before selection")
        assertTrue(renamedReporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val selectedEvent = emitted.last()
        identity.getOrCreateUser("Renamed while awaiting")
        assertTrue(helperB.confirmLead(incidentId))
        identity.getOrCreateUser("Renamed after confirmation")
        assertTrue(renamedReporter.resolve(incidentId))
        assertEquals(selectedEvent, eventDao.getEventById(selectedEvent.eventId))
        assertEquals("Reporter", incidentDao.getIncidentById(incidentId)!!.creatorName)
        assertEquals("Renamed after confirmation", emitted.last().actorName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun repositoryStartupRepairsPersistedWithdrawnSelection() = runTest {
        assertTrue(helperB.offerHelp(incidentId, "Nearby"))
        assertTrue(reporter.selectLead(incidentId, offerDao.getByHelper(incidentId, "helper-b-key")!!.offerId))
        val stuck = incidentDao.getIncidentById(incidentId)!!
        assertTrue(helperB.withdrawOffer(incidentId))
        incidentDao.insertOrUpdate(stuck)
        IncidentRepository(incidentDao, eventDao, identity("R"), gateway(), backgroundScope,
            offerDao = offerDao, eventSigning = TestSigner("reporter-key"))
        runCurrent()
        assertCleared(incidentDao.getIncidentById(incidentId)!!)
        assertEquals(stuck.version, incidentDao.getIncidentById(incidentId)!!.version)
    }

    private fun gateway(): MeshNetworkGateway = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
        arrayOf(MeshNetworkGateway::class.java)) { _, method, _ ->
        when (method.name) {
            "getMyDeviceName" -> "Reporter"
            "getMyNodeId" -> "R"
            "currentMeshTtl" -> 10
            "broadcastPriorityPayload", "broadcastPayload" ->
                com.example.testresqmesh.core.network.BroadcastDispatchResult(emptyMap())
            else -> null
        }
    } as MeshNetworkGateway

    private fun signedRevocation(incident: IncidentEntity, selectionId: String = incident.selectionId!!,
        version: Long = incident.version + 1): DomainEventEntity {
        val event = DomainEventEntity("revoke-$selectionId-$version", incidentId, "INCIDENT", "INCIDENT_LEAD_REVOKED",
            "R", "Reporter", version, System.currentTimeMillis(), JSONObject().put("selectionId", selectionId).toString())
        return event.copy(signature = TestSigner("reporter-key").sign(event))
    }

    private suspend fun replica(): Replica = Replica().also {
        it.incidents.insertOrUpdate(incidentDao.getIncidentById(incidentId)!!)
    }

    private suspend fun freshReplica(): Replica = Replica().also {
        it.incidents.insertOrUpdate(initialIncident)
    }

    private inner class Replica {
        val incidents = IncidentEntityFakeDao()
        val events = DomainEventEntityFakeDao()
        val offers = FakeOfferDao()
        val workflow = IncidentHelpWorkflow(null, incidents, offers, events, identity("R"), TestSigner("reporter-key")) { _, _ -> }

        suspend fun replay() {
            repeat(10) {
                var progressed = false
                events.getUnappliedForIncident(incidentId).forEach { if (workflow.apply(it)) progressed = true }
                if (!progressed) return
            }
            error("Replay did not settle")
        }
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
    fun offerRevisionIsIndependentAndSupersededSelectionConsumesReporterVersion() = runTest {
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
        assertTrue(reporter.apply(stale))
        assertEquals(IncidentState.OPEN.name, incidentDao.getIncidentById(incidentId)!!.status)
        assertEquals(2L, incidentDao.getIncidentById(incidentId)!!.version)
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
