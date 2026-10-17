package com.example.testresqmesh.core.domain.usecase.sos

import android.location.Location
import com.example.testresqmesh.core.domain.repository.SosAlertRepository
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.location.LocationStatus
import com.example.testresqmesh.core.model.SosActivity
import com.example.testresqmesh.core.model.SosAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaiseSosUseCaseTest {
    private val now = 10_000_000_000L

    @Test
    fun freshCachedFixKeepsCaptureTime() = runTest {
        val repo = FakeSos()
        val captured = now - 60_000L
        val id = RaiseSosUseCase(FakeLocation(fix(captured)), repo) { now }("MEDICAL")
        assertEquals("sos-1", id)
        assertEquals(Created("MEDICAL", 14.5, 121.0, 8f, captured), repo.created.single())
    }

    @Test
    fun staleCachedFixDropsCaptureTimeButKeepsCoordinates() = runTest {
        val repo = FakeSos()
        RaiseSosUseCase(FakeLocation(fix(now - SOS_CACHED_LOCATION_MAX_AGE_MS)), repo) { now }("FIRE")
        assertEquals(Created("FIRE", 14.5, 121.0, 8f, null), repo.created.single())
    }

    @Test
    fun nonPositiveCachedTimeIsNotTreatedAsFresh() = runTest {
        val repo = FakeSos()
        RaiseSosUseCase(FakeLocation(fix(0L)), repo) { now }("FIRE")
        RaiseSosUseCase(FakeLocation(fix(-5L)), repo) { now }("FIRE")
        assertTrue(repo.created.all { it.captured == null })
    }

    @Test
    fun missingCachedFixCreatesWithoutLocation() = runTest {
        val repo = FakeSos()
        RaiseSosUseCase(FakeLocation(null), repo) { now }("FIRE")
        assertEquals(Created("FIRE", null, null, null, null), repo.created.single())
    }

    @Test
    fun refinementIgnoredWhenNoLongerCurrent() = runTest {
        val repo = FakeSos()
        val location = FakeLocation(null)
        var launched = 0
        RaiseSosUseCase(location, repo) { now }.refineLocation("sos-1", isCurrent = { false }) { launched++ }
        location.deliver(fix(now - 1_000L))
        assertEquals(0, launched)
        assertTrue(repo.updates.isEmpty())
    }

    @Test
    fun refinementIgnoredWhenNoFixArrives() = runTest {
        val repo = FakeSos()
        val location = FakeLocation(null)
        var launched = 0
        RaiseSosUseCase(location, repo) { now }.refineLocation("sos-1", isCurrent = { true }) { launched++ }
        location.deliver(null)
        assertEquals(0, launched)
    }

    @Test
    fun currentRefinementUpdatesAndFallsBackToNowForMissingFixTime() = runTest {
        val repo = FakeSos()
        val location = FakeLocation(null)
        val blocks = mutableListOf<suspend () -> Unit>()
        val useCase = RaiseSosUseCase(location, repo) { now }
        useCase.refineLocation("sos-1", isCurrent = { true }) { blocks += it }
        location.deliver(fix(now - 2_000L))
        useCase.refineLocation("sos-1", isCurrent = { true }) { blocks += it }
        location.deliver(fix(0L))
        blocks.forEach { it() }
        assertEquals(listOf(now - 2_000L, now), repo.updates.map { it.second })
        assertEquals("sos-1", repo.updates.first().first)
    }

    private fun fix(time: Long): Location = object : Location("test") {
        override fun getTime() = time
        override fun getLatitude() = 14.5
        override fun getLongitude() = 121.0
        override fun getAccuracy() = 8f
    }

    private data class Created(
        val type: String,
        val lat: Double?,
        val lng: Double?,
        val accuracy: Float?,
        val captured: Long?
    )

    private class FakeLocation(private val cached: Location?) : LocationClient {
        private var pending: ((Location?) -> Unit)? = null
        override val locationStatus: StateFlow<LocationStatus> = MutableStateFlow(LocationStatus.IDLE)
        override fun startTracking(interval: Long) = Unit
        override fun stopTracking() = Unit
        override fun getLastKnownLocation(): Location? = cached
        override fun requestPinpointLocation(onResult: (Location?) -> Unit) { pending = onResult }
        fun deliver(location: Location?) { pending?.invoke(location) }
    }

    private class FakeSos : SosAlertRepository {
        val created = mutableListOf<Created>()
        val updates = mutableListOf<Pair<String, Long>>()
        override val alerts: StateFlow<List<SosAlert>> = MutableStateFlow(emptyList())
        override val ownActive: StateFlow<SosAlert?> = MutableStateFlow(null)
        override val incoming: StateFlow<SosAlert?> = MutableStateFlow(null)
        override fun sosActivity(id: String): Flow<List<SosActivity>> = emptyFlow()
        override fun isOwnSos(alert: SosAlert) = false
        override suspend fun create(
            type: String,
            lat: Double?,
            lng: Double?,
            accuracy: Float?,
            captured: Long?
        ): String {
            created += Created(type, lat, lng, accuracy, captured)
            return "sos-${created.size}"
        }
        override suspend fun updateLocation(id: String, lat: Double, lng: Double, accuracy: Float, captured: Long) {
            updates += id to captured
        }
        override suspend fun end(id: String) = Unit
        override suspend fun silence(id: String) = Unit
    }

    @Test
    fun maxAgeIsFifteenMinutes() {
        assertEquals(900_000L, SOS_CACHED_LOCATION_MAX_AGE_MS)
    }
}
