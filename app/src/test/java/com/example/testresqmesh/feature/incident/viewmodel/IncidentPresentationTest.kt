package com.example.testresqmesh.feature.incident.viewmodel

import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import org.junit.Assert.*
import org.junit.Test

class IncidentPresentationTest {
    private val incident = IncidentEntity("i", "r", "Reporter", "Medical", "Serious", "Description", "School",
        status = "OPEN", primaryResponderId = null, primaryResponderName = null, version = 1,
        createdAt = 1, updatedAt = 1, workflowVersion = 2, reporterSigningKey = "rk")
    private val b = IncidentOfferEntity("b", "i", "bk", "bn", "Miguel", "I have a first-aid kit", 1, false, 2)
    private val c = b.copy(offerId = "c", helperKey = "ck", helperName = "Clara", updatedAt = 3)
    private fun selected(status: String = "AWAITING_HELPER") = incident.copy(status = status,
        selectionId = "selection", selectionOfferId = "b", selectedHelperKey = "bk", selectionOfferRevision = 1)

    @Test fun visitorAndOfferOwnerHaveClearNextActions() {
        assertEquals(IncidentAction.OFFER, incidentPresentation(incident, emptyList(), "b", "bk").primary)
        val owner = incidentPresentation(incident, listOf(b), "b", "bk")
        assertEquals(IncidentAction.EDIT_OFFER, owner.primary)
        assertEquals(listOf(IncidentAction.WITHDRAW), owner.secondary)
        assertEquals("Wants to help", owner.helpers.single().label)
        assertEquals(b.note, owner.helpers.single().offer.note)
    }
    @Test fun selectedHelperMustConfirmBeforeConfirmedLabelAppears() {
        val waiting = incidentPresentation(selected(), listOf(b), "b", "bk")
        assertEquals(IncidentAction.CONFIRM, waiting.primary)
        assertEquals("Selected · Waiting for confirmation", waiting.helpers.single().label)
        val confirmed = incidentPresentation(selected("RESPONDING"), listOf(b), "b", "bk")
        assertNull(confirmed.primary)
        assertEquals(listOf(IncidentAction.WITHDRAW), confirmed.secondary)
        assertEquals("Confirmed helper", confirmed.helpers.single().label)
    }
    @Test fun changedSelectionReopensAndNeedsFreshSelection() {
        listOf(b.copy(revision = 2)).forEach { offer ->
            assertEquals(IncidentAction.EDIT_OFFER, incidentPresentation(selected(), listOf(offer), "b", "bk").primary)
            val reporter = incidentPresentation(selected("RESPONDING"), listOf(offer), "r", "rk")
            assertEquals(IncidentAction.REVIEW, reporter.primary)
            assertNull(reporter.selectedOffer)
            assertTrue(reporter.helpers.single().canChoose)
            assertFalse(IncidentAction.REVOKE in reporter.secondary)
        }
    }
    @Test fun selectedAndConfirmedOffersAreNotEditableButUnselectedOffersAre() {
        assertTrue(incidentPresentation(incident, listOf(b), "b", "bk").helpers.single().canEditOffer)
        listOf("AWAITING_HELPER", "RESPONDING", "RESOLVED", "CANCELLED").forEach { state ->
            assertFalse(incidentPresentation(selected(state), listOf(b), "b", "bk").helpers.single().canEditOffer)
        }
        assertFalse(incidentPresentation(incident, listOf(b), "c", "ck").helpers.single().canEditOffer)
        assertTrue(incidentPresentation(incident, listOf(b, c), "c", "ck").helpers.first { it.isMe }.canEditOffer)
    }

    @Test fun identityLoadingAndErrorsDoNotPresentVisitorsOrGrantActions() {
        val loading = incidentPresentation(incident, listOf(b), null, null)
        assertEquals("Checking your identity…", loading.warning)
        assertNull(loading.primary)
        assertFalse(loading.helpers.single().canChoose)
        val failed = incidentPresentation(incident, listOf(b), null, null, identityLoading = false,
            identityError = "Retry identity")
        assertEquals("Retry identity", failed.warning)
        assertNull(failed.primary)
        val wrongKey = incidentPresentation(incident, listOf(b), "r", "wrong")
        assertFalse(wrongKey.isReporter)
        assertFalse(isInvolved(incident, emptyList(), "r", "wrong"))
        assertNull(wrongKey.primary)
        assertTrue(wrongKey.warning!!.contains("another signing identity"))
        assertTrue(incidentPresentation(incident, listOf(b), "r", "rk").isReporter)
    }
    @Test fun withdrawnSelectionImmediatelyRemovesHelperAndAllowsAnotherOffer() {
        listOf("AWAITING_HELPER", "RESPONDING").forEach { state ->
            val stale = selected(state).copy(primaryResponderId = "b", primaryResponderName = "Miguel")
            val offers = listOf(b.copy(withdrawn = true, revision = 2), c)
            val reporter = incidentPresentation(stale, offers, "r", "rk")
            assertEquals("Looking for help", reporter.status)
            assertEquals(IncidentAction.REVIEW, reporter.primary)
            assertNull(reporter.selectedOffer)
            assertFalse(IncidentAction.REVOKE in reporter.secondary)
            assertTrue(reporter.helpers.single().canChoose)
            assertFalse(reporter.nextStep.contains("Miguel"))
            assertFalse(isInvolved(stale, offers, "b", "bk"))
            assertEquals(IncidentAction.OFFER, incidentPresentation(stale, offers, "b", "bk").primary)
        }
    }
    @Test fun reporterAuthorityAndTerminalStatesArePreserved() {
        assertTrue(incidentPresentation(incident, listOf(b), "r", "rk").helpers.single().canChoose)
        val mismatchedKey = incidentPresentation(incident, listOf(b), "r", "wrong")
        assertFalse(mismatchedKey.helpers.single().canChoose)
        assertTrue(mismatchedKey.secondary.isEmpty())
        assertEquals(IncidentAction.RESOLVE, incidentPresentation(selected("RESPONDING"), listOf(b), "r", "rk").primary)
        listOf("RESOLVED", "CANCELLED").forEach { state ->
            val terminal = incidentPresentation(selected(state), listOf(b), "r", "rk")
            assertNull(terminal.primary)
            assertTrue(terminal.secondary.isEmpty())
            assertFalse(terminal.helpers.single().canChoose)
            assertNull(incidentPresentation(selected(state), listOf(b.copy(withdrawn = true)), "r", "rk").warning)
        }
    }
    @Test fun helperOrderPrioritizesSelectionThenLocalOffer() {
        val d = c.copy(offerId = "d", helperKey = "dk", updatedAt = 100)
        val helpers = incidentPresentation(selected(), listOf(d, c, b), "c", "ck").helpers
        assertEquals(listOf("b", "c", "d"), helpers.map { it.offer.offerId })
        assertEquals(listOf("d", "c"), incidentPresentation(incident, listOf(d, c, b.copy(withdrawn = true)), "r", "rk").helpers.map { it.offer.offerId })
    }
    @Test fun involvementIncludesReporterOfferAndSelectionWithoutNullMatches() {
        assertTrue(isInvolved(incident, emptyList(), "r", "rk"))
        assertTrue(isInvolved(incident, listOf(b), "b", "bk"))
        assertTrue(isInvolved(selected(), emptyList(), "b", "bk"))
        assertFalse(isInvolved(incident, listOf(b.copy(withdrawn = true)), "b", "bk"))
        assertFalse(isInvolved(incident, emptyList(), null, null))
    }
    @Test fun removedSelectionExplainsTheChangeToTheFormerHelper() {
        val selected = DomainEventEntity("selected", "i", "INCIDENT", "INCIDENT_LEAD_SELECTED", "r", "Reporter", 2, 2,
            "{\"selectionId\":\"s\",\"helperKey\":\"bk\"}", applied = true)
        val revoked = selected.copy(eventId = "revoked", eventType = "INCIDENT_LEAD_REVOKED",
            payloadJson = "{\"selectionId\":\"s\"}")
        val view = incidentPresentation(incident, listOf(b), "b", "bk", listOf(selected, revoked))
        assertTrue(view.warning!!.contains("no longer the selected helper"))
        assertEquals(IncidentAction.EDIT_OFFER, view.primary)
        assertNull(incidentPresentation(incident, listOf(b), "b", "bk", listOf(selected, revoked.copy(applied = false))).warning)
    }
    @Test fun legacyActionsFollowExistingPolicy() {
        val legacy = incident.copy(workflowVersion = 1)
        assertEquals(IncidentAction.ASSIGN, incidentPresentation(legacy, emptyList(), "b", null).primary)
        assertEquals(listOf(IncidentAction.CANCEL), incidentPresentation(legacy, emptyList(), "r", null).secondary)
        val assigned = legacy.copy(status = "ASSIGNED", primaryResponderId = "b")
        val responder = incidentPresentation(assigned, emptyList(), "b", null)
        assertEquals(IncidentAction.START, responder.primary)
        assertTrue(IncidentAction.RESOLVE in responder.secondary)
        assertNull(incidentPresentation(assigned, emptyList(), "other", null).primary)
    }
    @Test fun displayTitleUsesTypeAndAreaWithoutChangingDescription() {
        assertEquals("Medical · School", incident.displayTitle())
        assertEquals("Medical emergency", incident.copy(areaDescription = "").displayTitle())
        assertEquals("Description", incident.description)
        assertEquals("Help moving an injured person", incident.copy(title = "Help moving an injured person").displayTitle())
    }

    @Test fun helperSummaryHandlesNoOffersOffersAndConfirmedHelper() {
        assertEquals("No offers yet", helperSummary(emptyList(), null, false))
        assertEquals("1 person offered help", helperSummary(listOf(b), null, false))
        assertEquals("2 people offered help", helperSummary(listOf(b, c), null, false))
        assertEquals("2 people offered help · You offered help", helperSummary(listOf(b, c), null, true))
        assertEquals("Miguel confirmed", helperSummary(listOf(b, c), "Miguel", false))
    }
}
