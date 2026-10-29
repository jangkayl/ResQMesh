package edu.cit.resqmesh.core.location

/** Fix quality for display. Coordinates are deliberately left out of the UI model. */
data class LocationFix(val accuracyMeters: Float, val capturedAt: Long)

/** "±12 m · updated 3 min ago". Ages under a minute read "just now". */
fun LocationFix.summary(now: Long): String {
    val minutes = (now - capturedAt).coerceAtLeast(0L) / MS_PER_MINUTE
    val age = when {
        minutes < 1 -> "updated just now"
        minutes < MINUTES_PER_HOUR -> "updated $minutes min ago"
        else -> "updated ${minutes / MINUTES_PER_HOUR} h ago"
    }
    return "±${accuracyMeters.toInt().coerceAtLeast(1)} m · $age"
}

const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
