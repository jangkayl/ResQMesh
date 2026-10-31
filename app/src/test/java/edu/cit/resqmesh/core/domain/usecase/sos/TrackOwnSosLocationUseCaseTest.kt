package edu.cit.resqmesh.core.domain.usecase.sos

import android.location.Location
import edu.cit.resqmesh.core.domain.repository.SosAlertRepository
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.location.LocationStatus
import edu.cit.resqmesh.core.model.SosActivity
import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.core.model.SosLocationSample
import edu.cit.resqmesh.core.model.SosRaiseCharge
import edu.cit.resqmesh.core.model.SosRaiseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackOwnSosLocationUseCaseTest {
    private val created = 100_000_000L
    private val now = created + 120_000L

    private fun alert(id: String = "sos") = SosAlert(id, "node", "name", "key", "Medical", 1, false, created, created,
        14.5, 121.0, 8f, created, false, "{}", "SENT")

    private fun fix(lat: Double, time: Long): Location = object : Location("test") {
        override fun getTime() = time
        override fun getLatitude() = lat
        override fun getLongitude() = 121.0
        override fun getAccuracy() = 30f
    }

    @Test
    fun movedFixPublishesAllowingLessAccuracyAndCancelClosesTheSession() = runTest {
        val location = FakeLocation()
        val repo = FakeRepo(alert())
        val job = launch { TrackOwnSosLocationUseCase(location, repo) { now }("sos") }
        runCurrent()
        assertEquals(listOf(SOS_LIVE_INTERVAL_MS), location.acquired)
        location.emit(fix(14.51, now))
        runCurrent()
        assertEquals(listOf(true), repo.updates)
        job.cancel()
        runCurrent()
        assertEquals(0, location.open)
    }

    @Test
    fun oldCachedFixIsNeverPublished() = runTest {
        val location = FakeLocation()
        val repo = FakeRepo(alert())
        location.emit(fix(14.51, now - SOS_CACHED_LOCATION_MAX_AGE_MS - 1))
        val job = launch { TrackOwnSosLocationUseCase(location, repo) { now }("sos") }
        runCurrent()
        assertTrue(repo.updates.isEmpty())
        job.cancel()
    }

    @Test
    fun sessionRelaxesAfterThirtyMinutes() = runTest {
        val location = FakeLocation()
        val job = launch { TrackOwnSosLocationUseCase(location, FakeRepo(alert())) { now }("sos") }
        advanceTimeBy(SOS_LIVE_RELAX_AFTER_MS)
        runCurrent()
        assertEquals(listOf(SOS_LIVE_INTERVAL_MS, SOS_LIVE_RELAXED_INTERVAL_MS), location.acquired)
        assertEquals(1, location.open)
        job.cancel()
        runCurrent()
        assertEquals(0, location.open)
    }

    @Test
    fun anotherSosIdStartsNothing() = runTest {
        val location = FakeLocation()
        TrackOwnSosLocationUseCase(location, FakeRepo(alert("other"))) { now }("sos")
        assertTrue(location.acquired.isEmpty())
    }

    private class FakeLocation : LocationClient {
        private val current = MutableStateFlow<Location?>(null)
        val acquired = mutableListOf<Long>()
        var open = 0
        override val locationStatus: StateFlow<LocationStatus> = MutableStateFlow(LocationStatus.IDLE)
        override val lastFix: StateFlow<LocationFix?> = MutableStateFlow(null)
        override val location: StateFlow<Location?> = current
        override fun startTracking(interval: Long) = Unit
        override fun stopTracking() = Unit
        override fun getLastKnownLocation(): Location? = current.value
        override fun requestPinpointLocation(onResult: (Location?) -> Unit) = Unit
        override fun acquireLive(intervalMs: Long, minDistanceMeters: Float): AutoCloseable {
            acquired += intervalMs
            open++
            return AutoCloseable { open-- }
        }
        fun emit(fix: Location) { current.value = fix }
    }

    private class FakeRepo(active: SosAlert) : SosAlertRepository {
        val updates = mutableListOf<Boolean>()
        override val alerts: StateFlow<List<SosAlert>> = MutableStateFlow(emptyList())
        override val ownActive: StateFlow<SosAlert?> = MutableStateFlow(active)
        override val incoming: StateFlow<SosAlert?> = MutableStateFlow(null)
        override fun sosActivity(id: String): Flow<List<SosActivity>> = emptyFlow()
        override fun isOwnSos(alert: SosAlert) = true
        override suspend fun create(type: String, lat: Double?, lng: Double?, accuracy: Float?, captured: Long?) = "sos"
        override suspend fun raise(
            type: String, charge: SosRaiseCharge, lat: Double?, lng: Double?, accuracy: Float?, captured: Long?
        ): SosRaiseResult = SosRaiseResult.Created("sos")
        override suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) {
            updates += false
        }
        override suspend fun updateMovedLocation(id: String, fix: SosLocationSample) { updates += true }
        override suspend fun end(id: String) = Unit
        override suspend fun silence(id: String) = Unit
    }
}
