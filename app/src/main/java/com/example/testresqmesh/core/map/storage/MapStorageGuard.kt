package com.example.testresqmesh.core.map.storage

import android.content.Context
import android.os.StatFs
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.utils.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Manages storage boundaries, atomic activation, and rollback for offline map packages.
 *
 * Guarantees:
 * - Versioned isolation: Each map version extracts into its own directory.
 * - Atomic activation: Written to temporary file and renamed only after successful verification.
 * - Rollback preservation: Interrupted, corrupt, or low-storage operations preserve the last verified package.
 */
class MapStorageGuard(
    private val baseDir: File,
    private val availableBytesProvider: (() -> Long)? = null
) {
    constructor(context: Context) : this(
        baseDir = File(context.filesDir, "offline_maps"),
        availableBytesProvider = null
    )

    val stagingDir: File = File(baseDir, "staging").apply { mkdirs() }
    val packagesDir: File = File(baseDir, "packages").apply { mkdirs() }
    private val activePointerFile: File = File(baseDir, "active_package.json")

    companion object {
        const val PMTILES_FILENAME = "cebu.pmtiles"
        const val STYLE_FILENAME = "style.json"
        const val DEFAULT_SAFETY_BUFFER_BYTES = 50L * 1024 * 1024 // 50 MB safety margin
    }

    /**
     * Checks if storage has sufficient bytes for downloading and extracting the package.
     */
    fun hasSufficientStorage(
        requiredBytes: Long,
        safetyBufferBytes: Long = DEFAULT_SAFETY_BUFFER_BYTES
    ): Boolean {
        val available = getAvailableBytes()
        val needed = requiredBytes + safetyBufferBytes
        val sufficient = available >= needed
        if (!sufficient) {
            AppLogger.d(
                "MapStorageGuard",
                "Insufficient storage: needed=$needed bytes (incl buffer), available=$available bytes"
            )
        }
        return sufficient
    }

    /**
     * Returns currently available storage bytes on the storage partition.
     */
    fun getAvailableBytes(): Long {
        return availableBytesProvider?.invoke() ?: run {
            val space = try { baseDir.usableSpace } catch (e: Exception) { 0L }
            if (space > 0L) {
                space
            } else {
                try {
                    val stat = StatFs(baseDir.path)
                    stat.availableBytes
                } catch (e: Exception) {
                    AppLogger.d("MapStorageGuard", "Failed to query storage: ${e.message}")
                    0L
                }
            }
        }
    }

    /**
     * Returns the staging file path for an in-progress download.
     */
    fun getStagingFile(filename: String): File {
        stagingDir.mkdirs()
        return File(stagingDir, "$filename.part")
    }

    /**
     * Cleans up staging files without touching installed packages.
     */
    fun cleanStaging() {
        try {
            stagingDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to clean staging dir: ${e.message}")
        }
    }

    /**
     * Retrieves the currently active installed package status.
     * Returns [MapPackageStatus.Installed] if valid, or [MapPackageStatus.NotInstalled] otherwise.
     */
    fun getActivePackageStatus(): MapPackageStatus {
        if (!activePointerFile.exists() || !activePointerFile.isFile) {
            return MapPackageStatus.NotInstalled
        }

        return try {
            val manifestJson = activePointerFile.readText()
            val manifest = MapPackageManifest.fromJson(manifestJson)
            val packageDir = File(packagesDir, "cebu-v${manifest.version}")
            val pmtilesFile = File(packageDir, PMTILES_FILENAME)
            val styleFile = File(packageDir, STYLE_FILENAME)

            if (packageDir.exists() && pmtilesFile.exists() && styleFile.exists()) {
                MapPackageStatus.Installed(
                    manifest = manifest,
                    packageDir = packageDir,
                    pmtilesFile = pmtilesFile,
                    styleFile = styleFile
                )
            } else {
                AppLogger.d("MapStorageGuard", "Active package files missing in ${packageDir.path}")
                MapPackageStatus.NotInstalled
            }
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to read active package manifest: ${e.message}")
            MapPackageStatus.NotInstalled
        }
    }

    /**
     * Extracts a package zip into a versioned temporary directory and validates its contents.
     * Returns the validated directory, or null if extraction/validation fails.
     */
    fun extractPackageZip(zipInputStream: InputStream, version: Int): File? {
        val targetDir = File(packagesDir, "cebu-v$version")
        val tempExtractDir = File(packagesDir, "cebu-v$version.tmp")

        try {
            if (tempExtractDir.exists()) {
                tempExtractDir.deleteRecursively()
            }
            tempExtractDir.mkdirs()

            ZipInputStream(zipInputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(tempExtractDir, entry.name)
                    // Guard against Zip Slip vulnerability
                    if (!entryFile.canonicalPath.startsWith(tempExtractDir.canonicalPath)) {
                        throw SecurityException("Zip entry attempted path traversal: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val pmtilesFile = File(tempExtractDir, PMTILES_FILENAME)
            val styleFile = File(tempExtractDir, STYLE_FILENAME)
            if (!pmtilesFile.exists() || !styleFile.exists()) {
                AppLogger.d(
                    "MapStorageGuard",
                    "Extracted package missing required assets: pmtiles=${pmtilesFile.exists()}, style=${styleFile.exists()}"
                )
                tempExtractDir.deleteRecursively()
                return null
            }

            // Validate style for zero-network policy and local dependency integrity
            val styleValidation = MapStyleValidator.validate(tempExtractDir, styleFile)
            if (styleValidation is MapStyleValidator.ValidationResult.Invalid) {
                AppLogger.d(
                    "MapStorageGuard",
                    "Extracted package style validation failed: ${styleValidation.reason}"
                )
                tempExtractDir.deleteRecursively()
                return null
            }

            // Replace destination directory
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            if (!tempExtractDir.renameTo(targetDir)) {
                // Fallback copy if atomic rename fails across boundaries
                tempExtractDir.copyRecursively(targetDir, overwrite = true)
                tempExtractDir.deleteRecursively()
            }

            return targetDir
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to extract package zip: ${e.message}")
            tempExtractDir.deleteRecursively()
            return null
        }
    }

    /**
     * Atomically activates a validated package by writing its manifest to the pointer file.
     * Retains the existing active package until the pointer write succeeds.
     */
    fun activatePackage(manifest: MapPackageManifest): Boolean {
        val tempPointerFile = File(baseDir, "active_package.json.tmp")
        return try {
            tempPointerFile.writeText(manifest.toJson())
            if (tempPointerFile.renameTo(activePointerFile)) {
                // Clean up any older versions
                pruneOldPackages(retainedVersion = manifest.version)
                true
            } else {
                tempPointerFile.copyTo(activePointerFile, overwrite = true)
                tempPointerFile.delete()
                pruneOldPackages(retainedVersion = manifest.version)
                true
            }
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to activate package atomically: ${e.message}")
            tempPointerFile.delete()
            false
        }
    }

    /**
     * Removes older package versions to save disk space, retaining only the currently active version.
     */
    private fun pruneOldPackages(retainedVersion: Int) {
        try {
            packagesDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != "cebu-v$retainedVersion") {
                    file.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to prune old packages: ${e.message}")
        }
    }

    /**
     * Deletes the currently active package and removes the pointer file.
     * Reverts status back to [MapPackageStatus.NotInstalled].
     */
    fun deleteActivePackage(): Boolean {
        return try {
            if (activePointerFile.exists()) {
                val manifest = try {
                    MapPackageManifest.fromJson(activePointerFile.readText())
                } catch (e: Exception) {
                    null
                }
                activePointerFile.delete()
                manifest?.let {
                    val packageDir = File(packagesDir, "cebu-v${it.version}")
                    if (packageDir.exists()) {
                        packageDir.deleteRecursively()
                    }
                }
            }
            cleanStaging()
            true
        } catch (e: Exception) {
            AppLogger.d("MapStorageGuard", "Failed to delete active package: ${e.message}")
            false
        }
    }
}

