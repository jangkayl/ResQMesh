package com.example.testresqmesh.feature.incident.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.feature.incident.ui.components.CreateIncidentSheet
import com.example.testresqmesh.feature.incident.ui.components.IncidentDetailSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class IncidentUiTest {
    @get:Rule val rule = createComposeRule()
    private val incident = IncidentEntity(
        incidentId = "i",
        creatorId = "r",
        creatorName = "Ana",
        incidentType = "Medical",
        severity = "Serious",
        description = "An injured person needs help reaching the school entrance.",
        areaDescription = "North entrance, school building",
        status = "OPEN",
        primaryResponderId = null,
        primaryResponderName = null,
        version = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        workflowVersion = 2,
        reporterSigningKey = "rk",
        title = "Help moving an injured person"
    )
    private val offer = IncidentOfferEntity(
        offerId = "offer",
        incidentId = "i",
        helperKey = "hk",
        helperNodeId = "hn",
        helperName = "Miguel",
        note = "I have a first-aid kit and can assist.",
        revision = 1,
        withdrawn = false,
        updatedAt = System.currentTimeMillis()
    )

    @Composable
    private fun Detail(
        value: IncidentEntity = incident,
        user: String = "h",
        key: String = "hk",
        offers: List<IncidentOfferEntity> = emptyList(),
        message: String? = null,
        onOffer: (String) -> Unit = {},
        onSelectLead: (String) -> Unit = {}
    ) {
        IncidentDetailSheet(
            incident = value,
            events = emptyList(),
            localUserId = user,
            offers = offers,
            localSigningKey = key,
            leadReachability = "Unavailable",
            actionBusy = false,
            actionMessage = message,
            onOffer = onOffer,
            onWithdrawOffer = {},
            onSelectLead = onSelectLead,
            onConfirmLead = {},
            onDeclineLead = {},
            onRevokeLead = {},
            onDismiss = {},
            onAcknowledge = {},
            onAssign = {},
            onStartResponse = {},
            onResolve = {},
            onCancel = {},
            onReleaseAssignment = {},
            onViewLocation = { _, _, _, _ -> }
        )
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val suffix = if (scale >= 1.5f) "-large-text" else ""
        val file = File(context.externalCacheDir, "incident-qa/$name$suffix.png")
        file.parentFile?.mkdirs()
        rule.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun visitorSeesSituationAndOfferActionWithoutOpeningTabs() {
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(user = "visitor", key = "visitor-key", offers = listOf(offer))
            }
        }
        rule.onNodeWithText("Help moving an injured person").assertIsDisplayed()
        rule.onNodeWithText("SERIOUS").assertIsDisplayed()
        rule.onNodeWithText("Looking for help").assertIsDisplayed()
        rule.onNodeWithText("People offering help (1)").assertIsDisplayed()
        rule.onNodeWithText("Offer help").assertIsDisplayed()
        screenshot("detail-daylight")
    }

    @Test
    fun reportRequiresTitleTypeUrgencyAndSubmits() {
        var submitted: List<String>? = null
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                CreateIncidentSheet(
                    onDismiss = {},
                    onSubmit = { title, type, severity, desc, _, _, _, _, _ ->
                        submitted = listOf(title, type, severity, desc)
                    },
                    submissionError = "Could not save. Try again."
                )
            }
        }
        rule.onNodeWithText("Report incident", substring = false).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Could not save. Try again.").performScrollTo().assertIsDisplayed()

        // Enter Title
        rule.onNodeWithText("Help moving an injured person").performTextInput("Need transport")

        // Select Emergency Type
        rule.onNodeWithText("Select an emergency type").performClick()
        rule.onNodeWithText("Medical").performClick()

        // Select Urgency
        rule.onNodeWithText("Serious — help urgently needed").performClick()

        // Submit
        rule.onNodeWithText("Report incident", substring = false).performScrollTo().performClick()
        assertEquals(listOf("Need transport", "Medical", "Serious", ""), submitted)
        screenshot("report-daylight")
    }

    @Test
    fun failedOfferRetainsNoteAndShowsRecoveryMessage() {
        var message by mutableStateOf<String?>(null)
        var sent = ""
        rule.setContent {
            TestResQMeshTheme {
                Detail(
                    message = message,
                    onOffer = { note ->
                        sent = note
                        message = "Could not save. Your draft is still here."
                    }
                )
            }
        }
        rule.onNodeWithText("Offer help").performClick()
        rule.onNodeWithText("e.g. I have a first-aid kit and can assist within 10 minutes.")
            .performTextInput("Can bring water")
        rule.onNodeWithText("Save help offer").performClick()
        assertEquals("Can bring water", sent)
        rule.onNodeWithText("Could not save. Your draft is still here.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reporterCanFindAndSelectHelperOffer() {
        var selectedOffer: String? = null
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(
                    user = "r",
                    key = "rk",
                    offers = listOf(offer),
                    onSelectLead = { selectedOffer = it }
                )
            }
        }
        rule.onNodeWithText("People offering help (1)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Miguel").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(offer.note).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Choose Miguel").performScrollTo().performClick()
        rule.onNodeWithText("Choose helper").performClick()
        assertEquals(offer.offerId, selectedOffer)
        screenshot("helpers-daylight")
    }

    @Test
    fun confirmedHelperIsDistinctFromConnectionAndHasNoConfirmAction() {
        val value = incident.copy(
            status = "RESPONDING",
            selectionId = "s",
            selectionOfferId = "offer",
            selectedHelperKey = "hk",
            selectionOfferRevision = 1,
            selectionConfirmedAt = System.currentTimeMillis()
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = true) {
                Detail(value, user = "r", key = "rk", offers = listOf(offer))
            }
        }
        rule.onAllNodesWithText("Confirm I can help").assertCountEquals(0)
        rule.onNodeWithText("People offering help (1)").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Miguel")[0].performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Confirmed helper").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Connection: Unavailable on mesh").performScrollTo().assertIsDisplayed()
        screenshot("helpers-night")
    }

    @Test
    fun withdrawnConfirmedHelperDisappearsAndReporterCanChooseAnotherOffer() {
        val value = incident.copy(status = "RESPONDING", selectionId = "s", selectionOfferId = offer.offerId,
            selectedHelperKey = offer.helperKey, selectionOfferRevision = 1, selectionConfirmedAt = 10,
            primaryResponderId = "h", primaryResponderName = "Miguel")
        val other = offer.copy(offerId = "other", helperKey = "ck", helperName = "Clara")
        var currentOffers by mutableStateOf(listOf(offer, other))
        var selected: String? = null
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(value, user = "r", key = "rk", offers = currentOffers,
                    onSelectLead = { selected = it })
            }
        }
        rule.onNodeWithText("CONFIRMED RESPONDER").assertExists()
        rule.runOnIdle { currentOffers = listOf(offer.copy(withdrawn = true, revision = 2), other) }
        rule.onAllNodesWithText("CONFIRMED RESPONDER").assertCountEquals(0)
        rule.onAllNodesWithText("Miguel").assertCountEquals(0)
        rule.onNodeWithText("Choose Clara").performScrollTo().performClick()
        rule.onNodeWithText("Choose helper").performClick()
        assertEquals(other.offerId, selected)
    }

    @Test
    fun selectedHelperCannotEditButCanWithdraw() {
        val value = incident.copy(status = "AWAITING_HELPER", selectionId = "s", selectionOfferId = offer.offerId,
            selectedHelperKey = offer.helperKey, selectionOfferRevision = 1)
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) { Detail(value, offers = listOf(offer)) }
        }
        rule.onAllNodesWithText("Edit offer").assertCountEquals(0)
        rule.onAllNodesWithText("Edit my offer").assertCountEquals(0)
        rule.onNodeWithText("This offer is selected. Withdraw before changing your assistance.")
            .performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Confirm I can help").assertCountEquals(1)
        rule.onNodeWithText("Withdraw").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reporterSnapshotAndOwnershipLabelSurviveDisplayNameChanges() {
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) { Detail(user = "r", key = "rk", offers = listOf(offer)) }
        }
        rule.onNodeWithText("Reported as Ana").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You reported this").assertExists()
        rule.onNodeWithText("Choose Miguel").performScrollTo().assertExists()
    }

    @Test
    fun terminalIncidentHasNoMutationActions() {
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(incident.copy(status = "RESOLVED"), user = "r", key = "rk", offers = listOf(offer))
            }
        }
        rule.onNodeWithText("Resolved").assertExists()
        rule.onAllNodesWithText("Offer help").assertCountEquals(0)
        rule.onAllNodesWithText("Choose Miguel").assertCountEquals(0)
        screenshot("resolved-daylight")
    }
}
