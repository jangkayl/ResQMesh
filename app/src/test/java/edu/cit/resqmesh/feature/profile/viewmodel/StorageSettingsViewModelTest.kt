package edu.cit.resqmesh.feature.profile.viewmodel

import edu.cit.resqmesh.core.storage.AppStorageCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var cache: File
    private val sosActive = MutableStateFlow(false)
    private var meshStops = 0
    private var wipes = 0
    private var wipeResult = true

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cache = Files.createTempDirectory("vm-cache").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cache.deleteRecursively()
    }

    private fun viewModel() = StorageSettingsViewModel(
        cleaner = AppStorageCleaner(cache) { wipes++; wipeResult },
        ownSosActive = sosActive,
        stopMesh = { meshStops++ },
        io = dispatcher
    )

    @Test
    fun clearCacheUpdatesSizeAndMessage() = runTest(dispatcher) {
        File(cache, "a.amr").writeBytes(ByteArray(2048))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2048L, vm.state.value.cacheBytes)

        vm.clearCache()
        advanceUntilIdle()

        assertEquals(0L, vm.state.value.cacheBytes)
        assertFalse(vm.state.value.busy)
        assertEquals("Cache cleared (2.0 KB freed)", vm.state.value.message)
    }

    @Test
    fun resetIsRefusedWhileOwnSosActive() = runTest(dispatcher) {
        sosActive.value = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetApp()
        advanceUntilIdle()

        assertEquals(0, meshStops)
        assertEquals(0, wipes)
        assertFalse(vm.state.value.resetting)
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun resetStopsMeshBeforeWiping() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetApp()
        runCurrent()

        assertTrue(vm.state.value.resetting)
        assertEquals(1, meshStops)
        assertEquals(0, wipes)

        advanceTimeBy(RESET_NOTICE_MS + 1)
        runCurrent()

        assertEquals(1, wipes)
        assertTrue(vm.state.value.busy)
    }

    @Test
    fun failedResetClearsBusyAndExplains() = runTest(dispatcher) {
        wipeResult = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.resetApp()
        advanceUntilIdle()

        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.resetting)
        assertTrue(vm.state.value.message!!.startsWith("Reset failed"))
    }
}
