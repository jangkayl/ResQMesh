package edu.cit.resqmesh.feature.profile.ui.offlinemap

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

/**
 * Plain-language text for a download failure. Maps the downloader's messages here rather than
 * rewording them, because [edu.cit.resqmesh.feature.profile.viewmodel.OfflineMapViewModel]
 * classifies errors by searching those messages.
 */
internal fun friendlyMapError(message: String): String = when {
    message.contains("fetch", ignoreCase = true) ->
        "We couldn't reach the map download. Check your internet and try again."
    message.contains("interrupted", ignoreCase = true) ->
        "The download stopped before it finished. Check your internet and try again."
    message.contains("extract", ignoreCase = true) || message.contains("activate", ignoreCase = true) ->
        "The map couldn't be installed. Please try again."
    else -> "Something went wrong with the download. Please try again."
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes.toDouble() / 1024
        String.format(Locale.US, "%.1f KB", kb)
    }
}
