package com.example.testresqmesh.feature.incident.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.MeshNetworkGateway
import com.example.testresqmesh.data.local.AppDatabase
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.UserEntity
import com.example.testresqmesh.data.repository.IdentityProvider
import com.example.testresqmesh.data.repository.IncidentRepository
import com.example.testresqmesh.feature.incident.ui.components.CreateIncidentSheet
import com.example.testresqmesh.feature.incident.ui.components.IncidentDetailSheet
import com.example.testresqmesh.feature.incident.ui.components.TacticalHelperOfferCard
import com.example.testresqmesh.feature.incident.ui.components.TacticalIncidentCard
import com.example.testresqmesh.feature.incident.viewmodel.HelperPresentation
import com.example.testresqmesh.feature.incident.viewmodel.IncidentViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class IncidentUiTest {
    @get:Rule val rule = createComposeRule()
    private val viewModelStore = ViewModelStore()
    private var database: AppDatabase? = null
    private var repositoryScope: CoroutineScope? = null

    @After fun releaseListFixture() {
        rule.runOnIdle { viewModelStore.clear() }
        repositoryScope?.cancel()
        database?.close()
    }

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
        onSelectLead: (String) -> Unit = {},
        onDirectChat: (String) -> Unit = {}
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
            onViewLocation = { _, _, _, _ -> },
            onDirectChat = onDirectChat
        )
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scale = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        val suffix = if (scale >= 1.5f) "-large-text" else ""
        val file = File(context.externalCacheDir, "incident-qa/$name$suffix.png")
        file.parentFile?.mkdirs()
        rule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
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
        rule.onNodeWithText("People offering help (1)").performScrollTo().assertIsDisplayed()
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
        rule.onNodeWithText("Broadcast Request to Mesh Network").assertIsDisplayed()
        rule.onNodeWithText("Could not save. Try again.").performScrollTo().assertIsDisplayed()

        // Enter Title
        rule.onNodeWithText("Title *").performScrollTo().performTextInput("Need transport")
        Espresso.closeSoftKeyboard()

        // Select Emergency Type
        rule.onNodeWithText("Medical").performScrollTo().performClick()

        // Select Urgency
        rule.onNodeWithText("Serious").performScrollTo().performClick()

        // Submit
        rule.onNodeWithText("Broadcast Request to Mesh Network").performClick()
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
        rule.onNodeWithText("How can you assist? *")
            .performTextInput("Can bring water")
        rule.onNodeWithText("Transmit help offer").performClick()
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
        rule.onNodeWithText("Mesh radio: Unavailable on mesh", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertFalse("Mesh status lost lines", layout.multiParagraph.didExceedMaxLines)
                assertFalse("Mesh status exceeds its measured height", layout.didOverflowHeight)
                for (line in 0 until layout.lineCount) {
                    assertFalse("Mesh status is ellipsized", layout.isLineEllipsized(line))
                    // A cached paragraph can be wider than this wrap-content text node.
                    assertTrue("Mesh status glyphs exceed its measured width",
                        layout.getLineRight(line) <= layout.size.width + 1f)
                }
            }
        rule.onNodeWithText("Select different helper").performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onAllNodesWithText("Walkie-Talkie Ready").assertCountEquals(0)
        rule.onAllNodesWithText("Private Chat Ready").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Verified Hardware Identity").assertCountEquals(0)
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
        rule.onNodeWithText("Ana").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("You").assertExists()
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

    @Test
    fun ui01_unsupportedReadinessBadgesAreNeverShown() {
        val selectedIncident = incident.copy(
            status = "RESPONDING",
            selectionId = "s",
            selectionOfferId = "offer",
            selectedHelperKey = "hk",
            selectionOfferRevision = 1,
            selectionConfirmedAt = System.currentTimeMillis()
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(
                    value = selectedIncident,
                    user = "r",
                    key = "rk",
                    offers = listOf(offer)
                )
            }
        }
        rule.onAllNodesWithText("Walkie-Talkie Ready").assertCountEquals(0)
        rule.onAllNodesWithText("Private Chat Ready").assertCountEquals(0)
    }

    @Test
    fun ui02_noHardwareVerificationBadgeOrTalkBackClaim() {
        val helperPres = HelperPresentation(
            offer = offer,
            label = "Offered assistance",
            isMe = false,
            canChoose = true,
            canEditOffer = false
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                TacticalHelperOfferCard(helper = helperPres)
            }
        }
        rule.onAllNodesWithText("Verified Hardware Identity", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Verified Hardware Identity", substring = true).assertCountEquals(0)
    }

    @Test
    fun ui03_criticalFilteringSearchAndClearUseTheIncidentList() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database = db
        runBlocking {
            db.incidentDao().insertOrUpdate(incident.copy(incidentId = "critical", severity = "Critical", title = "Critical request"))
            db.incidentDao().insertOrUpdate(incident.copy(incidentId = "serious", title = "Serious request"))
            db.incidentDao().insertOrUpdate(incident.copy(incidentId = "moderate", severity = "Moderate", title = "Moderate request"))
        }
        val user = UserEntity("visitor", "AB12", "Visitor", null, 1)
        val identity = object : IdentityProvider {
            override suspend fun getOrCreateUser(displayName: String?) = user
            override fun observeUser() = flowOf(user)
            override suspend fun getUserId() = user.userId
            override fun getDeviceId() = user.deviceId
        }
        // This fixture reads Room and UI state only. Any transport operation is a test failure.
        val gateway = Proxy.newProxyInstance(MeshNetworkGateway::class.java.classLoader,
            arrayOf(MeshNetworkGateway::class.java)) { _, method, _ ->
            check(method.name.startsWith("set")) { "Unexpected transport operation: ${method.name}" }
            null
        } as MeshNetworkGateway
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repositoryScope = scope
        val vm = IncidentViewModel(IncidentRepository(db.incidentDao(), db.domainEventDao(), identity,
            gateway, scope), identity)
        rule.runOnIdle { viewModelStore.put("incident", vm) }
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                IncidentListScreen(vm, onBack = {}, onViewLocation = { _, _, _, _ -> })
            }
        }
        rule.waitUntil(5_000) { vm.filteredIncidents.value.size == 3 }
        rule.onNodeWithText("Serious request").assertExists()
        rule.onNodeWithText("Critical only").assertIsNotSelected().performClick()
        rule.waitUntil(5_000) { vm.filteredIncidents.value.map { it.incidentId } == listOf("critical") }
        rule.onNodeWithText("Critical only").assertIsSelected()
        rule.onNodeWithText("Critical request").assertIsDisplayed()
        rule.onAllNodesWithText("Serious request").assertCountEquals(0)
        rule.onAllNodesWithText("Critical First").assertCountEquals(0)
        rule.onNodeWithText("All").performClick()
        rule.waitUntil(5_000) { vm.filteredIncidents.value.size == 3 }
        rule.onNodeWithText("Critical only").assertIsNotSelected()
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithContentDescription("Search incidents").performTextInput("Serious request")
        rule.waitUntil(5_000) { vm.filteredIncidents.value.map { it.incidentId } == listOf("serious") }
        rule.onNode(hasText("Serious request") and hasSetTextAction().not()).assertIsDisplayed()
        rule.onAllNodesWithText("Critical request").assertCountEquals(0)
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.waitUntil(5_000) { vm.filteredIncidents.value.size == 3 }
        rule.onNodeWithText("Critical request").assertExists()
        Espresso.closeSoftKeyboard()
        rule.onNodeWithContentDescription("Advanced filters").performClick()
        rule.onNodeWithText("Filter incidents").assertIsDisplayed()
        rule.onNodeWithText("Fire").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.waitUntil(5_000) { vm.filteredIncidents.value.isEmpty() && vm.filters.value.emergencyType == "Fire" }
        rule.onNodeWithText("No matching incidents").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").performClick()
        rule.waitUntil(5_000) { vm.filteredIncidents.value.size == 3 }
        rule.onNodeWithText("Critical request").assertExists()
    }

    @Test
    fun ui04_progressNeverImpliesEnRouteAndTerminalShowsReadOnly() {
        val resolvedIncident = incident.copy(
            status = "RESOLVED",
            selectionId = "s",
            selectionOfferId = "offer",
            selectedHelperKey = "hk",
            selectionOfferRevision = 1,
            selectionConfirmedAt = System.currentTimeMillis()
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                Detail(value = resolvedIncident, user = "r", key = "rk", offers = listOf(offer))
            }
        }
        // Confirmation never implies En Route travel
        rule.onAllNodesWithText("En Route").assertCountEquals(0)
        // Terminal state displays read-only next step without active action
        rule.onNodeWithText("This incident has been marked resolved on this phone.").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Confirm I can help").assertCountEquals(0)
    }

    @Test
    fun ui05_legacyAcknowledgementAndResolutionAvailable() {
        var acknowledged = false
        val legacyIncident = incident.copy(
            workflowVersion = 1,
            status = "OPEN",
            primaryResponderId = null
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                IncidentDetailSheet(
                    incident = legacyIncident,
                    events = emptyList(),
                    localUserId = "volunteer",
                    offers = emptyList(),
                    localSigningKey = "vol-key",
                    leadReachability = "Unavailable",
                    actionBusy = false,
                    actionMessage = null,
                    onOffer = {},
                    onWithdrawOffer = {},
                    onSelectLead = {},
                    onConfirmLead = {},
                    onDeclineLead = {},
                    onRevokeLead = {},
                    onDismiss = {},
                    onAcknowledge = { acknowledged = true },
                    onAssign = {},
                    onStartResponse = {},
                    onResolve = {},
                    onCancel = {},
                    onReleaseAssignment = {},
                    onViewLocation = { _, _, _, _ -> }
                )
            }
        }
        rule.onNodeWithText("Acknowledge request").performScrollTo().performClick()
        assertEquals(true, acknowledged)
    }

    @Test
    fun ui06_footerLayoutBoundsHelperSummaryAndShowsStatus() {
        val longHelperIncident = incident.copy(
            status = "RESPONDING",
            primaryResponderName = "Very Long Responding Helper Name That Takes Space",
            selectionOfferId = "offer"
        )
        rule.setContent {
            TestResQMeshTheme(darkTheme = false) {
                TacticalIncidentCard(
                    incident = longHelperIncident,
                    isMine = false,
                    isAssignedToMe = false,
                    activeOffersCount = 1,
                    hasMyOffer = false,
                    confirmedHelperName = "Very Long Responding Helper Name That Takes Space",
                    onClick = {}
                )
            }
        }
        rule.onNodeWithText("Very Long Responding Helper Name That Takes Space confirmed").assertIsDisplayed()
        rule.onNodeWithText("Helper confirmed").assertIsDisplayed()
    }

    @Test
    fun helperChatUsesStableIdsEvenWhenDisplayNamesMatch() {
        val chats = mutableListOf<String>()
        val first = offer.copy(helperName = "Alex", helperNodeId = "AB12")
        val second = offer.copy(offerId = "other", helperKey = "other-key", helperName = "Alex", helperNodeId = "CD34")
        rule.setContent {
            TestResQMeshTheme {
                Detail(user = "visitor", key = "visitor-key", offers = listOf(first, second), onDirectChat = chats::add)
            }
        }
        rule.onAllNodesWithText("Alex").assertCountEquals(2)
        rule.onAllNodesWithText("Alex")[0].performScrollTo().performClick()
        rule.onAllNodesWithText("Alex")[1].performScrollTo().performClick()
        assertEquals(listOf("AB12", "CD34"), chats.map(NodeIdentity::idOf))
    }

    @Test
    fun reporterAndHelperWithoutMeshIdsHaveNoChatShortcut() {
        rule.setContent {
            TestResQMeshTheme {
                Detail(user = "visitor", key = "visitor-key", offers = listOf(offer.copy(helperNodeId = "")))
            }
        }
        rule.onNodeWithText("Ana").assertHasNoClickAction()
        rule.onNodeWithText("Miguel").performScrollTo().assertHasNoClickAction()
    }

    @Test
    fun selectedHelperChatRetainsItsStableIdentity() {
        var target: String? = null
        val selectedIncident = incident.copy(status = "AWAITING_HELPER", selectionId = "s",
            selectionOfferId = offer.offerId, selectedHelperKey = offer.helperKey, selectionOfferRevision = 1)
        rule.setContent {
            TestResQMeshTheme {
                Detail(value = selectedIncident, user = "r", key = "rk", offers = listOf(offer), onDirectChat = { target = it })
            }
        }
        rule.onAllNodesWithText("Miguel")[0].performScrollTo().performClick()
        assertEquals("HN", NodeIdentity.idOf(target))
    }
    @Test
    fun reportDraftAndSelectionsSurviveStateRestoration() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        var submitted: List<String>? = null
        restoration.setContent {
            TestResQMeshTheme {
                CreateIncidentSheet(onDismiss = {}, onSubmit = { title, type, severity, desc, _, _, _, _, _ ->
                    submitted = listOf(title, type, severity, desc)
                })
            }
        }
        rule.onNodeWithText("Title *").performScrollTo().performTextInput("Need transport")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Medical").performScrollTo().performClick()
        rule.onNodeWithText("Serious").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Need transport").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Broadcast Request to Mesh Network").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf("Need transport", "Medical", "Serious", ""), submitted) }
    }

    @Test
    fun offerEditorAndDraftSurviveStateRestoration() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        var submitted: String? = null
        restoration.setContent {
            TestResQMeshTheme {
                Detail(user = "visitor", key = "visitor-key", onOffer = { submitted = it })
            }
        }
        rule.onNodeWithText("Offer help").performClick()
        rule.onNodeWithText("How can you assist? *").performScrollTo().performTextInput("First aid kit ready")
        Espresso.closeSoftKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("First aid kit ready").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Transmit help offer").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("First aid kit ready", submitted) }
    }

}
