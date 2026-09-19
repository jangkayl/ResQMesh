package com.example.testresqmesh.core.map.model

import java.io.File

/**
 * Lifecycle states of the offline map package.
 */
sealed class MapPackageStatus {
    /** No offline map package is installed on the device. */
    data object NotInstalled : MapPackageStatus()

    /** Downloading package components over Wi-Fi. */
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : MapPackageStatus()

    /** Verifying package integrity (signature verification or SHA-256 computation). */
    data class Verifying(val stage: String) : MapPackageStatus()

    /** Extracting package assets to versioned storage. */
    data class Extracting(val progressPercent: Int) : MapPackageStatus()

    /** Offline map package is verified, extracted, and ready for offline rendering. */
    data class Installed(
        val manifest: MapPackageManifest,
        val packageDir: File,
        val pmtilesFile: File,
        val styleFile: File
    ) : MapPackageStatus()

    /**
     * An error occurred during download, verification, extraction, or storage check.
     * If an existing package was already installed, [lastValidStatus] preserves it so
     * the app can safely rollback.
     */
    data class Error(
        val message: String,
        val lastValidStatus: MapPackageStatus? = null
    ) : MapPackageStatus()
}
