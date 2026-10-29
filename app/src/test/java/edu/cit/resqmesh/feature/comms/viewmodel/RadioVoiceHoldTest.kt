package edu.cit.resqmesh.feature.comms.viewmodel

import edu.cit.resqmesh.core.network.ratelimit.OutgoingChatBudget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioVoiceHoldTest {
    @Test fun twoHeldNotesBothSendOnRefillWithoutAnyUiSubscription() = runTest {
        val budget = OutgoingChatBudget(clock = { testScheduler.currentTime })
        while (budget.tryConsume()) Unit
        val sent = mutableListOf<String>()
        val hold = RadioVoiceHold(budget, backgroundScope) { _, audio -> sent += audio; true }
        assertTrue(hold.submit("1", "a"))
        assertTrue(hold.submit("1", "b")) // same instant: no debounce can swallow the second note
        runCurrent()
        assertEquals(emptyList<String>(), sent)
        assertEquals(2, hold.held.value)
        advanceTimeBy(3_000L); runCurrent()
        assertEquals(listOf("a"), sent)
        advanceTimeBy(3_000L); runCurrent()
        assertEquals(listOf("a", "b"), sent)
        assertEquals(0, hold.held.value)
    }

    @Test fun capRefusesTheSixthNote() = runTest {
        val budget = OutgoingChatBudget(clock = { testScheduler.currentTime })
        while (budget.tryConsume()) Unit
        val hold = RadioVoiceHold(budget, backgroundScope) { _, _ -> true }
        repeat(RadioVoiceHold.CAPACITY) { assertTrue(hold.submit("1", "n$it")) }
        assertFalse(hold.submit("1", "extra"))
        assertEquals(RadioVoiceHold.CAPACITY, hold.held.value)
    }

    @Test fun sendsImmediatelyWhenATokenIsAvailable() = runTest {
        val budget = OutgoingChatBudget(clock = { testScheduler.currentTime })
        val sent = mutableListOf<String>()
        val hold = RadioVoiceHold(budget, backgroundScope) { _, audio -> sent += audio; true }
        hold.submit("1", "a")
        assertEquals(listOf("a"), sent)
        assertEquals(19, budget.state.value.remaining)
    }

    @Test fun refusedNoteIsDroppedAndItsTokenRefunded() = runTest {
        val budget = OutgoingChatBudget(clock = { testScheduler.currentTime })
        val hold = RadioVoiceHold(budget, backgroundScope) { _, _ -> false }
        hold.submit("1", "a")
        assertEquals(0, hold.held.value)
        assertEquals(20, budget.state.value.remaining)
    }
}
