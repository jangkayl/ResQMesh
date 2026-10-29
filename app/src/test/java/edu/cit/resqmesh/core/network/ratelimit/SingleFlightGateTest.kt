package edu.cit.resqmesh.core.network.ratelimit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightGateTest {
    @Test
    fun overlappingRequestsRunOneAtATimeAndExtrasAreDropped() = runTest {
        val gate = SingleFlightGate("test", maxQueued = 1, log = {})
        val release = CompletableDeferred<Unit>()
        var running = 0
        var peak = 0
        var runs = 0
        suspend fun body() {
            running++; peak = maxOf(peak, running); runs++
            release.await()
            running--
        }
        val first = async { gate.run("a") { body() } }
        advanceUntilIdle()
        val second = async { gate.run("a") { body() } }
        advanceUntilIdle()
        val third = async { gate.run("a") { body() } }
        advanceUntilIdle()
        assertFalse(third.await())
        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(2, runs)
        assertEquals(1, peak)
    }

    @Test
    fun differentKeysDoNotBlockEachOther() = runTest {
        val gate = SingleFlightGate("test", log = {})
        val release = CompletableDeferred<Unit>()
        val blocked = async { gate.run("a") { release.await() } }
        advanceUntilIdle()
        var ran = false
        assertTrue(gate.run("b") { ran = true })
        assertTrue(ran)
        release.complete(Unit)
        assertTrue(blocked.await())
    }

    @Test
    fun gateIsReusableAfterFailure() = runTest {
        val gate = SingleFlightGate("test", log = {})
        runCatching { gate.run("a") { error("boom") } }
        assertTrue(gate.run("a") {})
    }
}
