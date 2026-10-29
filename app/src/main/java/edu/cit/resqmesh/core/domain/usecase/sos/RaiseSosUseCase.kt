package edu.cit.resqmesh.core.domain.usecase.sos

import edu.cit.resqmesh.core.domain.repository.SosAlertRepository
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.model.SosRaiseCharge
import edu.cit.resqmesh.core.model.SosRaiseResult

/** Cached fixes older than this are not sent as the SOS capture time. */
const val SOS_CACHED_LOCATION_MAX_AGE_MS = 15 * 60 * 1000L

/**
 * Raises an own SOS from the cached fix, then refines it with a pinpoint fix.
 * Callers own the coroutine scope and the replacement guard.
 */
class RaiseSosUseCase(
    private val locationClient: LocationClient,
    private val sosRepository: SosAlertRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** Creates the SOS; a cached time is kept only when positive and under 15 minutes old. */
    suspend operator fun invoke(sosType: String): String = when (val r = raise(sosType, SosRaiseCharge.NONE)) {
        is SosRaiseResult.Created -> r.id
        is SosRaiseResult.Reopened -> r.id
        SosRaiseResult.Throttled -> error("unreachable: no charge refuses")
    }

    /**
     * Like [invoke], but a NEW sosId first takes [charge] under the repository lock; a reopen is never charged.
     * With [includeLocation] false the SOS carries no coordinates and callers skip [refineLocation].
     */
    suspend fun raise(sosType: String, charge: SosRaiseCharge, includeLocation: Boolean = true): SosRaiseResult {
        val cached = if (includeLocation) locationClient.getLastKnownLocation() else null
        val validCachedTime = cached?.time?.takeIf { it > 0 && (now() - it) < SOS_CACHED_LOCATION_MAX_AGE_MS }
        return sosRepository.raise(
            sosType, charge, cached?.latitude, cached?.longitude, cached?.accuracy, validCachedTime
        )
    }

    /**
     * Requests a pinpoint fix for [id]. The result is dropped when [isCurrent] is false (SOS ended or
     * replaced meanwhile) or no fix arrived; otherwise the update runs through [launch].
     */
    fun refineLocation(id: String, isCurrent: () -> Boolean, launch: (suspend () -> Unit) -> Unit) {
        locationClient.requestPinpointLocation { location ->
            if (isCurrent() && location != null) launch {
                val validTime = location.time.takeIf { it > 0 } ?: now()
                sosRepository.updateLocation(id, location.latitude, location.longitude, location.accuracy, validTime)
            }
        }
    }
}
