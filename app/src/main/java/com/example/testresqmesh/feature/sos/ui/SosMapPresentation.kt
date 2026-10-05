package com.example.testresqmesh.feature.sos.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.model.ChatMessage
import org.maplibre.android.MapLibre
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.testresqmesh.feature.sos.ui.CoordinateFormatTab
import com.example.testresqmesh.feature.sos.ui.SosMapViewState

/**
 * Tactical Coordinate display formats.
 */
enum class CoordinateFormatTab {
    DECIMAL,
    MGRS,
    W3W
}

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
            is MapPackageStatus.Downloading -> "Map download in progress"
            is MapPackageStatus.Verifying -> "Map verification in progress"
            is MapPackageStatus.Extracting -> "Map extraction in progress"
            is MapPackageStatus.Error -> "Cebu offline map not installed"
            is MapPackageStatus.NotInstalled -> "Cebu offline map not installed"
            is MapPackageStatus.Installed -> "Offline map installed"
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

internal fun formatMgrsCoordinate(lat: Double, lng: Double): String {
    // Cebu Province sits in UTM Zone 51P
    val easting = ((lng - 123.0) * 100000.0).toInt().coerceIn(10000, 99999)
    val northing = ((lat - 9.0) * 100000.0).toInt().coerceIn(10000, 99999)
    return "51P XJ $easting $northing"
}

internal fun formatW3wCoordinate(lat: Double, lng: Double): String {
    val latInt = (lat * 100).toInt()
    val lngInt = (lng * 100).toInt()
    return "///cebu.beacon.loc%d%d".format(latInt, lngInt)
}

internal fun formatTimestamp(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}

internal fun bearingToCardinal(bearing: Double): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((bearing + 22.5) % 360) / 45).toInt()
    return directions[index.coerceIn(0, 7)]
}
