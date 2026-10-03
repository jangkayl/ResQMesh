package com.example.testresqmesh.feature.sos

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.layout.*
import com.example.testresqmesh.data.repository.IncidentEventSigning
import com.example.testresqmesh.data.repository.SosEvent
import com.example.testresqmesh.data.repository.SosProtocol
import com.example.testresqmesh.data.repository.SosIngestion
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.data.repository.SosRepository
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.sos.ui.SosThreadScreen
import com.example.testresqmesh.feature.comms.ui.PublicConversationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalLayoutApi::class)
class SosNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun matchAppWindowInsets() {
        compose.activityRule.scenario.onActivity { activity ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun capture(name: String) {
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "sos-ux").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun senderBackKeepsPersistentSosActive() = runBlocking {
        val koin = GlobalContext.get()
        val repo = koin.get<SosRepository>()
        val vm = koin.get<CommunicationViewModel>()
        val id = repo.create("Navigation test")
        repo.alerts.first { rows -> rows.any { it.sosId == id } }
        try {
            var left by mutableStateOf(false)
            compose.setContent {
                TestResQMeshTheme {
                    if (left) Text("Returned to app")
                    else SosThreadScreen(vm, koin.get<MediaHelper>(), id, onBack = { left = true }, onMap = {})
                }
            }
            capture("sender-thread")
            compose.onNodeWithTag("conversation_back").performClick()
            compose.onNodeWithText("Returned to app").assertIsDisplayed()
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
        } finally {
            repo.end(id)
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }

    @Test fun endingNeedsConfirmationAndProducesReadOnlyHistory() = runBlocking {
        val koin = GlobalContext.get()
        val repo = koin.get<SosRepository>()
        val vm = koin.get<CommunicationViewModel>()
        val id = repo.create("Confirmation test")
        repo.alerts.first { rows -> rows.any { it.sosId == id } }
        try {
            compose.setContent { TestResQMeshTheme { SosThreadScreen(vm, koin.get<MediaHelper>(), id, onBack = {}, onMap = {}) } }
            compose.onNodeWithTag("sos_end").performClick()
            compose.onNodeWithText("End your SOS?").assertIsDisplayed()
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
            compose.onNodeWithTag("sos_keep_active").performClick()
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
            compose.onNodeWithTag("sos_end").performClick()
            compose.onNodeWithTag("sos_confirm_end").performClick()
            repo.alerts.first { rows -> rows.any { it.sosId == id && it.ended } }
            compose.onNodeWithTag("conversation_read_only").assertIsDisplayed()
            compose.onNodeWithTag("conversation_send").assertDoesNotExist()
        } finally {
            repo.end(id)
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }

    @Test fun receiverBackSilencesOnlyThisPhone() = runBlocking {
        val koin = GlobalContext.get()
        val repo = koin.get<SosRepository>()
        val signing = koin.get<IncidentEventSigning>()
        val vm = koin.get<CommunicationViewModel>()
        val now = System.currentTimeMillis()
        val id = SosProtocol.prefix(signing.publicKey) + java.util.UUID.randomUUID()
        val unsigned = SosEvent(java.util.UUID.randomUUID().toString(), id, "remote-ui-test",
            "Remote test", signing.publicKey, "Medical", 1, false, now, now)
        val event = unsigned.copy(signature = signing.sign(unsigned.signingEvent()))
        assertEquals(SosIngestion.APPLIED, repo.ingest(event, live = false))
        repo.alerts.first { rows -> rows.any { it.sosId == id } }
        try {
            var left by mutableStateOf(false)
            compose.setContent {
                TestResQMeshTheme {
                    if (left) Text("Returned to app")
                    else SosThreadScreen(vm, koin.get<MediaHelper>(), id, onBack = { left = true }, onMap = {})
                }
            }
            compose.onNodeWithTag("sos_end").assertDoesNotExist()
            compose.onNodeWithTag("conversation_back").performClick()
            compose.onNodeWithText("Returned to app").assertIsDisplayed()
            repo.alerts.first { rows -> rows.any { it.sosId == id && it.locallySilenced } }
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
        } finally {
            val ended = unsigned.copy(eventId = java.util.UUID.randomUUID().toString(), revision = 2,
                ended = true, updatedAt = System.currentTimeMillis())
            repo.ingest(ended.copy(signature = signing.sign(ended.signingEvent())), live = false)
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }

    @Test fun largeTextCompactThreadKeepsComposerAndControlsAccessible() = runBlocking {
        val koin = GlobalContext.get()
        val repo = koin.get<SosRepository>()
        val vm = koin.get<CommunicationViewModel>()
        val id = repo.create("Compact test")
        repo.alerts.first { rows -> rows.any { it.sosId == id } }
        try {
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                    TestResQMeshTheme {
                        Box(Modifier.size(320.dp, 360.dp).testTag("compact_host")) {
                            SosThreadScreen(vm, koin.get<MediaHelper>(), id, onBack = {}, onMap = {})
                        }
                    }
                }
            }
            compose.onNodeWithTag("conversation_input").assertIsDisplayed()
            val host = compose.onNodeWithTag("compact_host").fetchSemanticsNode().boundsInRoot
            val input = compose.onNodeWithTag("conversation_input").fetchSemanticsNode().boundsInRoot
            assertTrue(input.bottom <= host.bottom)
            capture("compact-thread")
            compose.onNodeWithTag("sos_controls").performClick()
            compose.onNodeWithTag("sos_end").performScrollTo().assertIsDisplayed()
            capture("compact-controls")
            compose.onNodeWithTag("sos_end").performClick()
            compose.onNodeWithTag("sos_keep_active").performClick()
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
        } finally {
            repo.end(id)
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }

    @Test fun topReminderReservesSpaceAtLargeTextAndOpensSos() {
        var opened by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                TestResQMeshTheme {
                    Box(Modifier.size(320.dp, 300.dp).testTag("reminder_host")) {
                        SosReminderHost(true, { opened = true }) {
                            Column(Modifier.fillMaxSize().testTag("chat_content")) {
                                Spacer(Modifier.weight(1f))
                                Text("Composer", modifier = Modifier.testTag("test_composer"))
                            }
                        }
                    }
                }
            }
        }
        val reminder = compose.onNodeWithTag("sos_reminder").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("chat_content").fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("reminder_host").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("test_composer").fetchSemanticsNode().boundsInRoot
        assertTrue(reminder.bottom <= content.top)
        assertTrue(composer.bottom <= host.bottom)
        compose.onNodeWithTag("sos_reminder").performClick()
        assertTrue(opened)
    }

    @Test fun shellReminderReservesSpaceAboveNavigation() {
        compose.setContent {
            TestResQMeshTheme {
                ResQAppShell(ResQDestination.Mission, {}, {}, navigationReminder = {
                    ActiveSosReminder(onClick = {})
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Box(Modifier.fillMaxSize().testTag("shell_content"))
                    }
                }
            }
        }
        val reminder = compose.onNodeWithTag("sos_reminder").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("shell_content").fetchSemanticsNode().boundsInRoot
        assertTrue(content.bottom <= reminder.top)
    }

    @Test fun radioComposerUsesChannelWordingAndStaysBelowReminderWithKeyboard() = runBlocking {
        val koin = GlobalContext.get()
        val vm = koin.get<CommunicationViewModel>()
        val savedDraft = vm.conversationStates.value.firstOrNull { it.conversationId == "RADIO:2" }?.draft.orEmpty()
        vm.draft("RADIO:2", "")
        vm.conversationStates.first { rows -> rows.any { it.conversationId == "RADIO:2" && it.draft.isEmpty() } }
        val keyboardShown = java.util.concurrent.atomic.AtomicBoolean(false)
        val keyboardBottom = java.util.concurrent.atomic.AtomicInteger(0)
        try {
            compose.setContent {
                val visible = WindowInsets.isImeVisible
                val bottom = WindowInsets.ime.getBottom(LocalDensity.current)
                SideEffect { keyboardShown.set(visible); keyboardBottom.set(bottom) }
                TestResQMeshTheme(appearance = com.example.testresqmesh.core.ui.theme.AppAppearance.Daylight) {
                    SosReminderHost(true, {}) {
                        PublicConversationScreen(vm, koin.get<MediaHelper>(), "RADIO", "2",
                            title = "Radio 2", onBack = {})
                    }
                }
            }
            compose.onNodeWithText("Message this Radio channel…").assertIsDisplayed()
            compose.onNodeWithText("Reply to this SOS…").assertDoesNotExist()
            compose.onNodeWithTag("conversation_input").performClick()
            compose.onNodeWithTag("conversation_input").performTextInput("Radio draft")
            compose.waitUntil(10_000) { keyboardShown.get() && keyboardBottom.get() > 0 }
            compose.waitForIdle()
            val reminder = compose.onNodeWithTag("sos_reminder").fetchSemanticsNode().boundsInRoot
            val input = compose.onNodeWithTag("conversation_input").fetchSemanticsNode().boundsInRoot
            assertTrue(reminder.bottom <= input.top)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(input.bottom <= root.bottom - keyboardBottom.get())
            compose.onNodeWithTag("conversation_input").assertIsDisplayed()
            compose.onNodeWithTag("conversation_send").assertIsDisplayed()
            compose.onNodeWithTag("sos_reminder").assertIsDisplayed()
            capture("radio-keyboard-daylight")
        } finally {
            vm.draft("RADIO:2", savedDraft)
            vm.conversationStates.first { rows -> rows.any { it.conversationId == "RADIO:2" && it.draft == savedDraft } }
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }

    @Test fun keyboardControlsRemainOpenWhenKeyboardCloses() = runBlocking {
        val koin = GlobalContext.get()
        val repo = koin.get<SosRepository>()
        val vm = koin.get<CommunicationViewModel>()
        val id = repo.create("Keyboard test")
        repo.alerts.first { rows -> rows.any { it.sosId == id } }
        var availableHeight by mutableStateOf(380.dp)
        try {
            compose.setContent {
                TestResQMeshTheme {
                    Box(Modifier.height(availableHeight)) {
                        SosThreadScreen(vm, koin.get<MediaHelper>(), id, onBack = {}, onMap = {})
                    }
                }
            }
            compose.onNodeWithTag("conversation_input").performTextInput("Keyboard reply")
            compose.onNodeWithTag("sos_controls").performClick()
            compose.runOnIdle { availableHeight = 720.dp }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("sos_controls_dialog").assertIsDisplayed()
            capture("keyboard-controls-restored-height")
            assertFalse(repo.alerts.value.first { it.sosId == id }.ended)
        } finally {
            repo.end(id)
            androidx.lifecycle.ViewModelStore().apply { put("vm", vm); clear() }
        }
    }
}
