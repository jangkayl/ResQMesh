package edu.cit.resqmesh.feature.sos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import edu.cit.resqmesh.core.map.model.MapPackageManifest
import edu.cit.resqmesh.core.map.model.MapPackageStatus
import edu.cit.resqmesh.core.model.ChatMessage
import org.maplibre.android.MapLibre
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import edu.cit.resqmesh.feature.sos.ui.SosMapViewState

/** Id prefix of the preview message built when an incident opens this map. */
internal const val INCIDENT_MAP_ID_PREFIX = "incident_map_"

private const val METERS_PER_KILOMETER = 1000f

/**
 * Presentation state for [SosMapScreen].
 */
sealed class SosMapViewState {
    /** Alert does not contain GPS coordinates. */
    data object NoLocation : SosMapViewState()

    /**
     * Alert contains valid coordinates, but a verified offline vector map package
     * is not active. Fallback displays high-vis decimal coordinates, sender, and status.
     */
    data class MaplessFallback(
        val lat: Double,
        val lng: Double,
        val formattedLat: String,
        val formattedLng: String,
        val statusMessage: String,
        val showInstallAction: Boolean = true,
        val isFallbackActive: Boolean = true
    ) : SosMapViewState()

    /**
     * Alert contains valid coordinates and a verified offline vector map package
     * is active. MapLibre Native renders the local PMTiles package via local style.
     */
    data class MapReady(
        val lat: Double,
        val lng: Double,
        val formattedLat: String,
        val formattedLng: String,
        val styleFile: File,
        val manifest: MapPackageManifest,
        val attribution: String
    ) : SosMapViewState()
}

/**
 * Resolves the deterministic [SosMapViewState] given an alert message and storage package status.
 */
fun resolveSosMapViewState(
    alertMessage: ChatMessage,
    packageStatus: MapPackageStatus
): SosMapViewState {
    val lat = alertMessage.locationLat
    val lng = alertMessage.locationLng
    if (lat == null || lng == null) {
        return SosMapViewState.NoLocation
    }

    return if (packageStatus is MapPackageStatus.Installed) {
        SosMapViewState.MapReady(
            lat = lat,
            lng = lng,
            formattedLat = formatCoordinate(lat, isLat = true),
            formattedLng = formatCoordinate(lng, isLat = false),
            styleFile = packageStatus.styleFile,
            manifest = packageStatus.manifest,
            attribution = packageStatus.manifest.attribution.ifBlank { "© OpenStreetMap contributors" }
        )
    } else {
        val statusMsg = when (packageStatus) {
            is MapPackageStatus.Downloading -> "Your map is downloading"
            is MapPackageStatus.Verifying -> "Getting your map ready"
            is MapPackageStatus.Extracting -> "Getting your map ready"
            is MapPackageStatus.Error -> "No map on this phone yet"
            is MapPackageStatus.NotInstalled -> "No map on this phone yet"
            is MapPackageStatus.Installed -> "Your map is ready"
        }
        SosMapViewState.MaplessFallback(
            lat = lat,
            lng = lng,
            formattedLat = formatCoordinate(lat, isLat = true),
            formattedLng = formatCoordinate(lng, isLat = false),
            statusMessage = statusMsg,
            showInstallAction = packageStatus is MapPackageStatus.NotInstalled || packageStatus is MapPackageStatus.Error
        )
    }
}

internal fun formatCoordinate(coord: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (coord >= 0) "N" else "S"
    } else {
        if (coord >= 0) "E" else "W"
    }
    return String.format(Locale.US, "%.5f° %s", kotlin.math.abs(coord), dir)
}

internal fun formatTimestamp(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}

internal fun bearingToDirectionName(bearing: Double): String {
    val directions = arrayOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
    val index = (((bearing + 22.5) % 360) / 45).toInt()
    return directions[index.coerceIn(0, 7)]
}

/** Headline and supporting line for how far away the location is. */
internal fun describeDistance(distanceMeters: Float, bearingDegrees: Float): Pair<String, String> {
    val distance = if (distanceMeters < METERS_PER_KILOMETER) {
        "${distanceMeters.roundToInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", distanceMeters / METERS_PER_KILOMETER)
    }
    val direction = bearingToDirectionName(bearingDegrees.toDouble())
    return "About $distance away" to "$direction of you. In a straight line, not by road."
}

/** Previews opened from chat, radio and incidents are not SOS alerts and must not read as one. */
internal fun mapLocationTitle(message: ChatMessage): String = when {
    message.isSOS -> "SOS location"
    message.id.startsWith(INCIDENT_MAP_ID_PREFIX) -> "Incident location"
    else -> "Shared location"
}

internal fun mapPinLabel(message: ChatMessage): String = if (message.isSOS) "SOS" else "PIN"

/** Only real alerts carry a sent time; previews are stamped when they are opened. */
internal fun mapSentTime(message: ChatMessage): String? =
    if (message.isSOS) formatTimestamp(message.timestamp) else null

internal fun copyableCoordinates(lat: Double, lng: Double): String =
    String.format(Locale.US, "%.6f, %.6f", lat, lng)
