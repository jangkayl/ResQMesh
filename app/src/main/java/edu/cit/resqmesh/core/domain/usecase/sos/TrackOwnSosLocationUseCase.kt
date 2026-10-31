package edu.cit.resqmesh.core.domain.usecase.sos

import android.location.Location
import edu.cit.resqmesh.core.domain.repository.SosAlertRepository
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.location.isFixUsable
import edu.cit.resqmesh.core.model.SosLocationSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

const val SOS_LIVE_INTERVAL_MS = 15_000L
const val SOS_LIVE_RELAXED_INTERVAL_MS = 60_000L
const val SOS_LIVE_RELAX_AFTER_MS = 30 * 60 * 1000L
private const val SOS_LIVE_MIN_DISTANCE_M = 5f

/**
 * Follows the phone while its own SOS [invoke]s, publishing only what [SosLocationPublisher] allows.
 * Runs until cancelled. Callers must not start it for an SOS that excluded location.
 */
class TrackOwnSosLocationUseCase(
    private val locationClient: LocationClient,
    private val sosRepository: SosAlertRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(sosId: String) {
        val createdAt = sosRepository.ownActive.value?.takeIf { it.sosId == sosId }?.createdAt ?: return
        var session = locationClient.acquireLive(SOS_LIVE_INTERVAL_MS, SOS_LIVE_MIN_DISTANCE_M)
        try {
            coroutineScope {
                launch {
                    delay((SOS_LIVE_RELAX_AFTER_MS - (now() - createdAt)).coerceAtLeast(0))
                    val relaxed = locationClient.acquireLive(SOS_LIVE_RELAXED_INTERVAL_MS, SOS_LIVE_MIN_DISTANCE_M)
                    session.close()
                    session = relaxed
                }
                locationClient.location.filterNotNull().collect { publish(sosId, it) }
            }
        } finally {
            session.close()
        }
    }

    private suspend fun publish(sosId: String, location: Location) {
        val alert = sosRepository.ownActive.value?.takeIf { it.sosId == sosId }
        val fix = SosLocationSample(location.latitude, location.longitude, location.accuracy, location.time)
        // The first value is whatever fix was cached; an old one says nothing about where the person is now.
        val current = isFixUsable(location.time, location.accuracy, now(), SOS_CACHED_LOCATION_MAX_AGE_MS)
        val decision =
            if (alert != null && current) SosLocationPublisher.decide(alert, fix) else SosLocationDecision.SKIP
        try {
            when (decision) {
                SosLocationDecision.SKIP -> Unit
                SosLocationDecision.PUBLISH_MOVED -> sosRepository.updateMovedLocation(sosId, fix)
                SosLocationDecision.PUBLISH -> sosRepository.updateLocation(
                    sosId, fix.latitude, fix.longitude, fix.accuracy, fix.capturedAt
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // One failed revision must not stop the SOS; the next fix tries again.
        }
    }
}
