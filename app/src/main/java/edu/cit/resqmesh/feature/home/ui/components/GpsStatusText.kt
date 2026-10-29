package edu.cit.resqmesh.feature.home.ui.components

import edu.cit.resqmesh.core.location.LocationFix
import edu.cit.resqmesh.core.location.LocationStatus
import edu.cit.resqmesh.core.location.summary

internal data class GpsStatusText(val title: String, val description: String)

/** Home wording per status; READY includes fix accuracy and age when known. */
internal fun gpsStatusText(status: LocationStatus, fix: LocationFix?, now: Long): GpsStatusText = when (status) {
    LocationStatus.ACQUIRING -> GpsStatusText("Finding your location...", "Kept ready so an SOS can include it.")
    LocationStatus.READY -> GpsStatusText(
        "Location found",
        fix?.let { "${it.summary(now)} · only sent with an SOS" } ?: "Only sent when you send an SOS or share it."
    )
    LocationStatus.ERROR_DENIED -> GpsStatusText(
        "Location permission needed",
        "Allow location so rescuers can find you."
    )
    LocationStatus.ERROR_DISABLED -> GpsStatusText(
        "Phone location is off",
        "Turn it on so an SOS can include your position."
    )
    LocationStatus.IDLE -> GpsStatusText("Location idle", "Position will be found when you send an SOS.")
    LocationStatus.OFF -> GpsStatusText("Auto-detect off", "You can still include location when sending an SOS.")
}
