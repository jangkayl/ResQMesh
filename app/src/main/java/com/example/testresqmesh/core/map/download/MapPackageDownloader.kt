package com.example.testresqmesh.core.map.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.map.verifier.ManifestVerifier
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks connectivity conditions before downloading large offline packages.
 */
interface ConnectivityProvider {
    fun isWifiConnected(): Boolean
}

class AndroidConnectivityProvider(private val context: Context) : ConnectivityProvider {
    override fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Exception) {
            AppLogger.d("AndroidConnectivityProvider", "Failed to check connectivity: ${e.message}")
            false
        }
    }
}

/**
 * Pluggable HTTP transport for downloading manifest, signature, and asset zip.
 */
interface HttpTransport {
    suspend fun fetchBytes(urlString: String): ByteArray?
    suspend fun downloadToFile(
        urlString: String,
        destinationFile: File,
        resumeOffset: Long = 0L,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean
}

class DefaultHttpTransport : HttpTransport {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    override suspend fun fetchBytes(urlString: String): ByteArray? {
        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            AppLogger.d("DefaultHttpTransport", "Failed to fetch bytes from $urlString: ${e.message}")
            null
        }
    }

    override suspend fun downloadToFile(
        urlString: String,
        destinationFile: File,
        resumeOffset: Long,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean {
        return try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (resumeOffset > 0) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isOk = responseCode == HttpURLConnection.HTTP_OK

            if (!isOk && !isPartial) {
                AppLogger.d("DefaultHttpTransport", "Download failed with HTTP $responseCode")
                return false
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (isPartial) contentLength + resumeOffset else contentLength

            destinationFile.parentFile?.mkdirs()
            val append = isPartial && resumeOffset > 0
            var bytesReadTotal = if (append) resumeOffset else 0L

            FileOutputStream(destinationFile, append).use { fos ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        bytesReadTotal += read
                        onProgress(bytesReadTotal, totalBytes)
                    }
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.d("DefaultHttpTransport", "Error downloading to file: ${e.message}")
            false
        }
    }
}

/**
 * Downloads, verifies, and installs the offline map package according to strict safety rules:
 * - Downloads to staging `.part` file.
 * - Checks Wi-Fi requirement.
 * - Verifies Ed25519 manifest signature against maintainer public key.
 * - Computes and verifies SHA-256 digest of package zip.
 * - Checks storage limits before starting.
 * - Extracts into versioned directory.
 * - Atomically activates on completion.
 * - Preserves last verified package on any error or interruption.
 */
class MapPackageDownloader(
    private val storageGuard: MapStorageGuard,
    private val verifier: ManifestVerifier,
    private val connectivityProvider: ConnectivityProvider,
    private val httpTransport: HttpTransport = DefaultHttpTransport(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Executes the package download and verification pipeline.
     */
    fun downloadAndInstallPackage(
        manifestUrl: String,
        signatureUrl: String,
        maintainerPublicKey: ByteArray,
        requireWifi: Boolean = true
    ): Flow<MapPackageStatus> = channelFlow {
        val currentStatus = storageGuard.getActivePackageStatus()

        // 1. Wi-Fi guard
        if (requireWifi && !connectivityProvider.isWifiConnected()) {
            send(MapPackageStatus.Error("Wi-Fi connection required to download offline map.", currentStatus))
            return@channelFlow
        }

        // 2. Fetch manifest & signature
        send(MapPackageStatus.Verifying("Fetching package manifest & signature"))
        val manifestBytes = httpTransport.fetchBytes(manifestUrl)
        val signatureBytes = httpTransport.fetchBytes(signatureUrl)

        if (manifestBytes == null || signatureBytes == null) {
            send(MapPackageStatus.Error("Failed to fetch map manifest or signature.", currentStatus))
            return@channelFlow
        }

        // 3. Verify digital signature of manifest
        send(MapPackageStatus.Verifying("Verifying manifest digital signature"))
        val isSignatureValid = verifier.verifyManifestSignature(
            manifestBytes = manifestBytes,
            signatureBytes = signatureBytes,
            publicKeyBytes = maintainerPublicKey
        )

        if (!isSignatureValid) {
            send(MapPackageStatus.Error("Manifest digital signature verification failed.", currentStatus))
            return@channelFlow
        }

        // 4. Parse manifest and verify storage
        val manifest = try {
            MapPackageManifest.fromJson(String(manifestBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            send(MapPackageStatus.Error("Failed to parse manifest JSON: ${e.message}", currentStatus))
            return@channelFlow
        }

        // Estimated space needed: package zip + uncompressed pmtiles/style (~2x zip size)
        val estimatedSpaceNeeded = manifest.byteLength * 2
        if (!storageGuard.hasSufficientStorage(estimatedSpaceNeeded)) {
            send(MapPackageStatus.Error("Insufficient storage space for offline map package.", currentStatus))
            return@channelFlow
        }

        // 5. Download package zip to staging .part file
        val stagingFile = storageGuard.getStagingFile("cebu-v${manifest.version}.zip")
        val resumeOffset = if (stagingFile.exists()) stagingFile.length() else 0L

        send(MapPackageStatus.Downloading(
            progressPercent = if (manifest.byteLength > 0) ((resumeOffset * 100) / manifest.byteLength).toInt() else 0,
            bytesDownloaded = resumeOffset,
            totalBytes = manifest.byteLength
        ))

        val downloadSuccess = httpTransport.downloadToFile(
            urlString = manifest.assetUrl,
            destinationFile = stagingFile,
            resumeOffset = resumeOffset,
            onProgress = { downloaded, total ->
                val totalExpected = if (total > 0) total else manifest.byteLength
                val percent = if (totalExpected > 0) ((downloaded * 100) / totalExpected).toInt().coerceIn(0, 100) else 0
                trySend(
                    MapPackageStatus.Downloading(
                        progressPercent = percent,
                        bytesDownloaded = downloaded,
                        totalBytes = totalExpected
                    )
                )
            }
        )

        if (!downloadSuccess || !stagingFile.exists()) {
            send(MapPackageStatus.Error("Download interrupted or failed.", currentStatus))
            return@channelFlow
        }

        // 6. Verify SHA-256 checksum
        send(MapPackageStatus.Verifying("Verifying package SHA-256 checksum"))
        val isHashValid = verifier.verifyPackageHash(stagingFile, manifest.sha256)
        if (!isHashValid) {
            stagingFile.delete()
            send(MapPackageStatus.Error("Package checksum verification failed. File corrupted.", currentStatus))
            return@channelFlow
        }

        // 7. Extract to versioned storage
        send(MapPackageStatus.Extracting(50))
        val packageDir = try {
            FileInputStream(stagingFile).use { fis ->
                storageGuard.extractPackageZip(fis, manifest.version)
            }
        } catch (e: Exception) {
            null
        }

        if (packageDir == null) {
            stagingFile.delete()
            send(MapPackageStatus.Error("Failed to extract offline map package assets.", currentStatus))
            return@channelFlow
        }

        // 8. Atomically activate new package
        val activated = storageGuard.activatePackage(manifest)
        // Clean staging file now that extraction and activation are complete
        stagingFile.delete()

        if (!activated) {
            send(MapPackageStatus.Error("Failed to atomically activate new map package.", currentStatus))
            return@channelFlow
        }

        // 9. Emit installed status
        val installedStatus = storageGuard.getActivePackageStatus()
        send(installedStatus)
    }.flowOn(ioDispatcher)
}
