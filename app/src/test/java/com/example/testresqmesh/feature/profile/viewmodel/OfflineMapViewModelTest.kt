package com.example.testresqmesh.feature.profile.viewmodel

import com.example.testresqmesh.core.map.catalog.MapPackageCatalogEntry
import com.example.testresqmesh.core.map.download.ConnectivityProvider
import com.example.testresqmesh.core.map.download.HttpTransport
import com.example.testresqmesh.core.map.download.MapPackageDownloader
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.map.verifier.ManifestVerifier
import com.example.testresqmesh.core.map.verifier.SignatureVerifier
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.feature.sos.ui.SosMapViewState
import com.example.testresqmesh.feature.sos.ui.resolveSosMapViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var storageGuard: MapStorageGuard
    private var isWifiConnected = true

    private val connectivityProvider = object : ConnectivityProvider {
        override fun isWifiConnected(): Boolean = isWifiConnected
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("offline_map_vm_test").toFile()
        storageGuard = MapStorageGuard(tempDir)
        isWifiConnected = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun createZipArchive(files: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in files) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun createSampleManifest(
        version: Int = 1,
        byteLength: Long = 1024L,
        sha256: String = "dummy-sha"
    ) = MapPackageManifest(
        packageId = "cebu-offline",
        version = version,
        minLat = 9.40,
        minLng = 123.30,
        maxLat = 11.40,
        maxLng = 124.20,
        minZoom = 6,
        maxZoom = 15,
        byteLength = byteLength,
        sha256 = sha256,
        releaseDate = "2026-09-19",
        attribution = "© OpenStreetMap contributors",
        assetUrl = "https://example.com/cebu-v$version.zip"
    )

    @Test
    fun initialState_notInstalled_displaysCatalogMetadata() {
        val verifier = ManifestVerifier()
        val downloader = MapPackageDownloader(storageGuard, verifier, connectivityProvider)
        val viewModel = OfflineMapViewModel(storageGuard, downloader, connectivityProvider)

        val state = viewModel.uiState.value
        assertEquals(MapPackageStatus.NotInstalled, state.packageStatus)
        assertEquals("cebu-offline", state.catalogEntry.packageId)
        assertEquals(1, state.catalogEntry.targetVersion)
        assertFalse(state.isDownloadingOrInstalling)
        assertFalse(state.isUpdateAvailable)
    }

    @Test
    fun startDownload_wifiDisconnected_triggersWifiRequiredWarning() = runTest {
        isWifiConnected = false
        val verifier = ManifestVerifier()
        val downloader = MapPackageDownloader(storageGuard, verifier, connectivityProvider)
        val viewModel = OfflineMapViewModel(storageGuard, downloader, connectivityProvider)

        viewModel.startDownload(forceWifiBypass = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.wifiRequiredWarning)
        assertEquals("Wi-Fi connection required to download offline map.", state.errorMessage)
        assertFalse(state.isDownloadingOrInstalling)
    }

    @Test
    fun startDownload_insufficientStorage_triggersStorageError() = runTest {
        // Mock a guard that reports only 5 MB of space
        val tightStorageGuard = MapStorageGuard(tempDir, availableBytesProvider = { 5L * 1024 * 1024 })
        val verifier = ManifestVerifier()
        val downloader = MapPackageDownloader(tightStorageGuard, verifier, connectivityProvider)
        val viewModel = OfflineMapViewModel(tightStorageGuard, downloader, connectivityProvider)

        viewModel.startDownload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.insufficientStorageError)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Insufficient storage"))
        assertFalse(state.isDownloadingOrInstalling)
    }

    @Test
    fun startDownload_corruptPackage_setsCorruptErrorAndPreservesSafety() = runTest {
        // Setup valid signature but corrupt zip hash
        val fakeVerifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val manifest = createSampleManifest(version = 1, sha256 = "expected-correct-hash")
        val manifestBytes = manifest.toJson().toByteArray()

        val fakeTransport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray = manifestBytes
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ): Boolean {
                destinationFile.writeBytes("corrupted-content-mismatched-hash".toByteArray())
                return true
            }
        }

        val downloader = MapPackageDownloader(
            storageGuard = storageGuard,
            verifier = fakeVerifier,
            connectivityProvider = connectivityProvider,
            httpTransport = fakeTransport,
            ioDispatcher = testDispatcher
        )

        val viewModel = OfflineMapViewModel(
            storageGuard = storageGuard,
            downloader = downloader,
            connectivityProvider = connectivityProvider
        )

        viewModel.startDownload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.corruptPackageError)
        assertFalse(state.isDownloadingOrInstalling)
        assertTrue(state.errorMessage!!.contains("checksum verification failed", ignoreCase = true))
        // Staging file was discarded for security
        assertEquals(MapPackageStatus.NotInstalled, storageGuard.getActivePackageStatus())
    }

    @Test
    fun downloadAndInstall_success_updatesToInstalled() = runTest {
        val fakeVerifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "pmtiles-payload".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8}".toByteArray()
            )
        )
        val validHash = sha256Hex(zipBytes)
        val manifest = createSampleManifest(version = 1, byteLength = zipBytes.size.toLong(), sha256 = validHash)

        val fakeTransport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray = manifest.toJson().toByteArray()
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ): Boolean {
                destinationFile.writeBytes(zipBytes)
                onProgress(zipBytes.size.toLong(), zipBytes.size.toLong())
                return true
            }
        }

        val downloader = MapPackageDownloader(
            storageGuard = storageGuard,
            verifier = fakeVerifier,
            connectivityProvider = connectivityProvider,
            httpTransport = fakeTransport,
            ioDispatcher = testDispatcher
        )

        val viewModel = OfflineMapViewModel(
            storageGuard = storageGuard,
            downloader = downloader,
            connectivityProvider = connectivityProvider
        )

        viewModel.startDownload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.packageStatus is MapPackageStatus.Installed)
        val installed = state.packageStatus as MapPackageStatus.Installed
        assertEquals(1, installed.manifest.version)
        assertEquals(manifest, state.activeManifest)
        assertFalse(state.isDownloadingOrInstalling)
        assertNull(state.errorMessage)
    }

    @Test
    fun deletePackage_revertsStateToNotInstalled() = runTest {
        // Pre-activate a package
        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8}".toByteArray()
            )
        )
        storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        val manifest = createSampleManifest(version = 1)
        storageGuard.activatePackage(manifest)

        val verifier = ManifestVerifier()
        val downloader = MapPackageDownloader(storageGuard, verifier, connectivityProvider)
        val viewModel = OfflineMapViewModel(storageGuard, downloader, connectivityProvider)

        assertTrue(viewModel.uiState.value.packageStatus is MapPackageStatus.Installed)

        viewModel.deletePackage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MapPackageStatus.NotInstalled, state.packageStatus)
        assertNull(state.activeManifest)
    }

    @Test
    fun updateAvailable_detectedWhenTargetVersionExceedsInstalledVersion() {
        // Active package is version 1
        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8}".toByteArray()
            )
        )
        storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        val manifest = createSampleManifest(version = 1)
        storageGuard.activatePackage(manifest)

        // Catalog entry specifies version 2
        val catalogV2 = MapPackageCatalogEntry(targetVersion = 2)

        val verifier = ManifestVerifier()
        val downloader = MapPackageDownloader(storageGuard, verifier, connectivityProvider)
        val viewModel = OfflineMapViewModel(
            storageGuard = storageGuard,
            downloader = downloader,
            connectivityProvider = connectivityProvider,
            catalogEntry = catalogV2
        )

        val state = viewModel.uiState.value
        assertTrue(state.isUpdateAvailable)
        assertEquals(1, state.activeManifest?.version)
        assertEquals(2, state.catalogEntry.targetVersion)
    }

    @Test
    fun downloaderToActivePackageWiring_enablesSosMapScreenMapReady() = runTest {
        val sosAlert = ChatMessage(
            id = "alert-test-01",
            senderName = "Operator Bravo",
            text = "Need field extraction",
            imageBase64 = null,
            audioBase64 = null,
            locationLat = 10.315696,
            locationLng = 123.885437,
            isMine = false,
            isSOS = true
        )

        // 1. Initial State: No package installed -> SosMapScreen shows MaplessFallback
        val initialMapState = resolveSosMapViewState(sosAlert, storageGuard.getActivePackageStatus())
        assertTrue(initialMapState is SosMapViewState.MaplessFallback)
        val initialFallback = initialMapState as SosMapViewState.MaplessFallback
        assertEquals("Cebu offline map not installed", initialFallback.statusMessage)

        // 2. Download and verify package via Downloader
        val fakeVerifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "vector-data".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8, \"layers\": []}".toByteArray()
            )
        )
        val validHash = sha256Hex(zipBytes)
        val manifest = createSampleManifest(version = 1, byteLength = zipBytes.size.toLong(), sha256 = validHash)

        val fakeTransport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray = manifest.toJson().toByteArray()
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ): Boolean {
                destinationFile.writeBytes(zipBytes)
                return true
            }
        }

        val downloader = MapPackageDownloader(
            storageGuard = storageGuard,
            verifier = fakeVerifier,
            connectivityProvider = connectivityProvider,
            httpTransport = fakeTransport,
            ioDispatcher = testDispatcher
        )

        val viewModel = OfflineMapViewModel(storageGuard, downloader, connectivityProvider)
        viewModel.startDownload()
        advanceUntilIdle()

        // 3. Post-Install State: Storage guard activates package -> SosMapScreen resolves to MapReady
        val activeStatus = storageGuard.getActivePackageStatus()
        assertTrue(activeStatus is MapPackageStatus.Installed)

        val activeMapState = resolveSosMapViewState(sosAlert, activeStatus)
        assertTrue(activeMapState is SosMapViewState.MapReady)
        val mapReady = activeMapState as SosMapViewState.MapReady
        assertTrue(mapReady.styleFile.exists())
        assertEquals(10.315696, mapReady.lat, 0.000001)
        assertEquals(123.885437, mapReady.lng, 0.000001)
        assertFalse(mapReady.styleFile.absolutePath.startsWith("http"))
    }
}
