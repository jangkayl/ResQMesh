package edu.cit.resqmesh.core.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowThrottleTest {
    @Test
    fun firstValueImmediateAndFinalValueOfBurstDelivered() = runTest {
        val received = mutableListOf<Int>()
        val source = flow {
            for (i in 1..10) { emit(i); delay(10) }
        }
        val job = launch { source.throttleLatest(300).collect { received += it } }
        runCurrent()
        assertEquals(listOf(1), received)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(10, received.last())
        assert(received.size < 10)
        job.cancel()
    }
}
