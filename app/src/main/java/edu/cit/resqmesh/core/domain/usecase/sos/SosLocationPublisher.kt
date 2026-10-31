package edu.cit.resqmesh.core.domain.usecase.sos

import edu.cit.resqmesh.core.model.SosAlert
import edu.cit.resqmesh.core.model.SosLocationSample
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val SOS_LOCATION_MIN_GAP_MS = 60_000L
const val SOS_LOCATION_RELAXED_GAP_MS = 5 * 60 * 1000L
const val SOS_LOCATION_RELAX_AFTER_MS = 10 * 60 * 1000L
const val SOS_LOCATION_MIN_MOVE_M = 25.0
private const val ACCURACY_IMPROVEMENT_FACTOR = 0.5f
private const val EARTH_RADIUS_M = 6_371_000.0

enum class SosLocationDecision {
    SKIP,
    /** Better or first position, under the repository's normal accuracy rule. */
    PUBLISH,
    /** The person moved, so a less accurate fix is still the truer position. */
    PUBLISH_MOVED
}

/**
 * Decides whether a live fix is worth a signed mesh revision. The alert itself records the last published
 * fix, so a process restart cannot reset the pacing. A stationary person publishes nothing.
 */
object SosLocationPublisher {
    fun decide(alert: SosAlert, fix: SosLocationSample): SosLocationDecision {
        val lat = alert.latitude
        val lng = alert.longitude
        val published = alert.locationCapturedAt
        return when {
            alert.ended -> SosLocationDecision.SKIP
            lat == null || lng == null || published == null -> SosLocationDecision.PUBLISH
            fix.capturedAt - published < minGap(alert, fix) -> SosLocationDecision.SKIP
            moved(lat, lng, fix) -> SosLocationDecision.PUBLISH_MOVED
            improved(alert.accuracyMeters, fix.accuracy) -> SosLocationDecision.PUBLISH
            else -> SosLocationDecision.SKIP
        }
    }

    private fun minGap(alert: SosAlert, fix: SosLocationSample) =
        if (fix.capturedAt - alert.createdAt > SOS_LOCATION_RELAX_AFTER_MS) SOS_LOCATION_RELAXED_GAP_MS
        else SOS_LOCATION_MIN_GAP_MS

    private fun moved(lat: Double, lng: Double, fix: SosLocationSample) =
        distanceMeters(lat, lng, fix.latitude, fix.longitude) >= max(SOS_LOCATION_MIN_MOVE_M, fix.accuracy.toDouble())

    private fun improved(old: Float?, new: Float) = old != null && new <= old * ACCURACY_IMPROVEMENT_FACTOR

    /** Haversine, kept here so the rule stays a plain JVM unit. */
    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
