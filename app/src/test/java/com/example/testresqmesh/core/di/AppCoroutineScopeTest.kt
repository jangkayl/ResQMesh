package com.example.testresqmesh.core.di

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppCoroutineScopeTest {
    @Test
    fun injectedDispatcherRunsRepositoryWorkDeterministically() = runTest {
        val owner = AppCoroutineScope(StandardTestDispatcher(testScheduler))
        var completed = false

        owner.scope.launch { completed = true }

        assertFalse(completed)
        advanceUntilIdle()
        assertTrue(completed)
        owner.close()
    }

    @Test
    fun closeCancelsOutstandingRepositoryWork() = runTest {
        val owner = AppCoroutineScope(StandardTestDispatcher(testScheduler))
        val child = owner.scope.launch { awaitCancellation() }

        owner.close()
        advanceUntilIdle()

        assertTrue(child.isCancelled)
    }
}
