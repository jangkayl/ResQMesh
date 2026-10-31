package edu.cit.resqmesh.core.map.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import edu.cit.resqmesh.core.map.model.MapPackageManifest
import edu.cit.resqmesh.core.map.model.MapPackageStatus
import edu.cit.resqmesh.core.map.storage.MapStorageGuard
import edu.cit.resqmesh.core.map.verifier.ManifestVerifier
import edu.cit.resqmesh.core.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
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
            AppLogger.d("AndroidConnectivityProvider", "Failed to check connectivity: ${e.javaClass.simpleName}")
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
        private const val MAX_REDIRECTS = 5
        private const val HTTP_TEMPORARY_REDIRECT = 307
        private const val HTTP_PERMANENT_REDIRECT = 308
        private const val COPY_BUFFER_BYTES = 8192
        private val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER, HTTP_TEMPORARY_REDIRECT, HTTP_PERMANENT_REDIRECT
        )
    }

    private fun openConnectionWithRedirects(urlString: String, resumeOffset: Long = 0L): HttpURLConnection {
        var currentUrl = urlString
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                if (resumeOffset > 0) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }
            if (connection.responseCode !in REDIRECT_CODES) return connection
            val newLocation = connection.getHeaderField("Location")
            connection.disconnect()
            if (newLocation.isNullOrBlank()) return connection
            currentUrl = if (newLocation.startsWith("http://") || newLocation.startsWith("https://")) {
                newLocation
            } else {
                URL(URL(currentUrl), newLocation).toString()
            }
            redirectCount++
        }
        throw java.io.IOException("Too many redirects connecting to: $urlString")
    }

    override suspend fun fetchBytes(urlString: String): ByteArray? {
        return try {
            val connection = openConnectionWithRedirects(urlString)
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.d("DefaultHttpTransport", "Fetch bytes failed with HTTP $responseCode for $urlString")
                connection.disconnect()
                return null
            }
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            AppLogger.d("DefaultHttpTransport", "Failed to fetch bytes from $urlString: ${e.javaClass.simpleName}")
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
            val connection = openConnectionWithRedirects(urlString, resumeOffset)

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isOk = responseCode == HttpURLConnection.HTTP_OK

            if (!isOk && !isPartial) {
                AppLogger.d("DefaultHttpTransport", "Download failed with HTTP $responseCode")
                connection.disconnect()
                return false
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (isPartial) contentLength + resumeOffset else contentLength

            destinationFile.parentFile?.mkdirs()
            val append = isPartial && resumeOffset > 0
            var bytesReadTotal = if (append) resumeOffset else 0L

            FileOutputStream(destinationFile, append).use { fos ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        bytesReadTotal += read
                        onProgress(bytesReadTotal, totalBytes)
                    }
                }
            }

            // Guard against premature stream termination
            if (totalBytes > 0 && bytesReadTotal < totalBytes) {
                AppLogger.d(
                    "DefaultHttpTransport",
                    "Download truncated prematurely: read $bytesReadTotal of $totalBytes bytes"
                )
                return false
            }

            true
        } catch (e: Exception) {
            AppLogger.d("DefaultHttpTransport", "Error downloading to file: ${e.javaClass.simpleName}")
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

        val manifest = fetchVerifiedManifest(manifestUrl, signatureUrl, maintainerPublicKey, currentStatus)
            ?: return@channelFlow

        // Estimated space needed: package zip + uncompressed pmtiles/style (~2x zip size)
        val estimatedSpaceNeeded = manifest.byteLength * ESTIMATED_EXPANSION_FACTOR
        if (!storageGuard.hasSufficientStorage(estimatedSpaceNeeded)) {
            send(MapPackageStatus.Error("Insufficient storage space for offline map package.", currentStatus))
            return@channelFlow
        }

        val stagingFile = downloadVerifiedPackage(manifest, currentStatus) ?: return@channelFlow
        installPackage(manifest, stagingFile, currentStatus)
    }.flowOn(ioDispatcher)

    /** Steps 2-4: fetches the manifest and accepts it only with a valid maintainer signature. */
    private suspend fun ProducerScope<MapPackageStatus>.fetchVerifiedManifest(
        manifestUrl: String,
        signatureUrl: String,
        maintainerPublicKey: ByteArray,
        currentStatus: MapPackageStatus
    ): MapPackageManifest? {
        send(MapPackageStatus.Verifying("Fetching package manifest & signature"))
        val manifestBytes = httpTransport.fetchBytes(manifestUrl)
        val signatureBytes = httpTransport.fetchBytes(signatureUrl)

        if (manifestBytes == null || signatureBytes == null) {
            send(MapPackageStatus.Error("Failed to fetch map manifest or signature.", currentStatus))
            return null
        }

        send(MapPackageStatus.Verifying("Verifying manifest digital signature"))
        val isSignatureValid = verifier.verifyManifestSignature(
            manifestBytes = manifestBytes,
            signatureBytes = signatureBytes,
            publicKeyBytes = maintainerPublicKey
        )

        if (!isSignatureValid) {
            send(MapPackageStatus.Error("Manifest digital signature verification failed.", currentStatus))
            return null
        }

        return try {
            MapPackageManifest.fromJson(String(manifestBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            send(MapPackageStatus.Error("Failed to parse manifest JSON: ${e.message}", currentStatus))
            null
        }
    }

    /** Steps 5-6: returns the staged zip once its SHA-256 matches the manifest. */
    private suspend fun ProducerScope<MapPackageStatus>.downloadVerifiedPackage(
        manifest: MapPackageManifest,
        currentStatus: MapPackageStatus
    ): File? {
        val stagingFile = storageGuard.getStagingFile("cebu-v${manifest.version}.zip")
        val resumeOffset = if (stagingFile.exists()) stagingFile.length() else 0L

        send(MapPackageStatus.Downloading(
            progressPercent = percentOf(resumeOffset, manifest.byteLength),
            bytesDownloaded = resumeOffset,
            totalBytes = manifest.byteLength
        ))

        val downloadSuccess = httpTransport.downloadToFile(
            urlString = manifest.assetUrl,
            destinationFile = stagingFile,
            resumeOffset = resumeOffset,
            onProgress = { downloaded, total ->
                val totalExpected = if (total > 0) total else manifest.byteLength
                trySend(
                    MapPackageStatus.Downloading(
                        progressPercent = percentOf(downloaded, totalExpected).coerceIn(0, PERCENT),
                        bytesDownloaded = downloaded,
                        totalBytes = totalExpected
                    )
                )
            }
        )

        if (!downloadSuccess || !stagingFile.exists()) {
            send(MapPackageStatus.Error("Download interrupted or failed.", currentStatus))
            return null
        }

        send(MapPackageStatus.Verifying("Verifying package SHA-256 checksum"))
        val isHashValid = verifier.verifyPackageHash(stagingFile, manifest.sha256)
        if (!isHashValid) {
            stagingFile.delete()
            send(MapPackageStatus.Error("Package checksum verification failed. File corrupted.", currentStatus))
            return null
        }
        return stagingFile
    }

    /** Steps 7-9: extracts to versioned storage, then atomically activates the new package. */
    private suspend fun ProducerScope<MapPackageStatus>.installPackage(
        manifest: MapPackageManifest,
        stagingFile: File,
        currentStatus: MapPackageStatus
    ) {
        send(MapPackageStatus.Extracting(EXTRACTING_PROGRESS_PERCENT))
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
            return
        }

        val activated = storageGuard.activatePackage(manifest)
        // Clean staging file now that extraction and activation are complete
        stagingFile.delete()

        if (!activated) {
            send(MapPackageStatus.Error("Failed to atomically activate new map package.", currentStatus))
            return
        }

        send(storageGuard.getActivePackageStatus())
    }

    private fun percentOf(done: Long, total: Long): Int = if (total > 0) ((done * PERCENT) / total).toInt() else 0

    private companion object {
        const val PERCENT = 100
        const val ESTIMATED_EXPANSION_FACTOR = 2
        const val EXTRACTING_PROGRESS_PERCENT = 50
    }
}
