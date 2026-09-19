package com.example.testresqmesh.feature.sos.ui

import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.model.ChatMessage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SosMapScreenTest {

    private lateinit var tempDir: File
    private lateinit var storageGuard: MapStorageGuard

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("sos_map_test").toFile()
        storageGuard = MapStorageGuard(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createSosMessage(
        id: String = "sos-01",
        sender: String = "Operator Alfa",
        lat: Double? = 10.315696,
        lng: Double? = 123.885437,
        text: String = "Medical emergency at checkpoint"
    ) = ChatMessage(
        id = id,
        senderName = sender,
        text = text,
        imageBase64 = null,
        audioBase64 = null,
        locationLat = lat,
        locationLng = lng,
        isMine = false,
        isSOS = true
    )

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

    @Test
    fun noLocation_returnsNoLocationViewState() {
        val messageWithoutLocation = createSosMessage(lat = null, lng = null)
        val state = resolveSosMapViewState(messageWithoutLocation, MapPackageStatus.NotInstalled)
        assertTrue(state is SosMapViewState.NoLocation)
    }

    @Test
    fun partialCoordinates_returnsNoLocationViewState() {
        val messageMissingLng = createSosMessage(lat = 10.3157, lng = null)
        val state = resolveSosMapViewState(messageMissingLng, MapPackageStatus.NotInstalled)
        assertTrue(state is SosMapViewState.NoLocation)
    }

    @Test
    fun notInstalled_returnsMaplessFallbackWithAccurateCoordinates() {
        val sos = createSosMessage(lat = 10.315696, lng = 123.885437)
        val state = resolveSosMapViewState(sos, MapPackageStatus.NotInstalled)

        assertTrue(state is SosMapViewState.MaplessFallback)
        val fallback = state as SosMapViewState.MaplessFallback
        assertEquals(10.315696, fallback.lat, 0.000001)
        assertEquals(123.885437, fallback.lng, 0.000001)
        assertEquals("10.31570° N", fallback.formattedLat)
        assertEquals("123.88544° E", fallback.formattedLng)
        assertEquals("Cebu offline map not installed", fallback.statusMessage)
        assertTrue(fallback.isFallbackActive)
    }

    @Test
    fun errorState_returnsMaplessFallbackWithoutHidingCoordinates() {
        val sos = createSosMessage(lat = -12.046374, lng = -77.042793) // South, West coordinates
        val errorStatus = MapPackageStatus.Error("Corrupt archive", null)
        val state = resolveSosMapViewState(sos, errorStatus)

        assertTrue(state is SosMapViewState.MaplessFallback)
        val fallback = state as SosMapViewState.MaplessFallback
        assertEquals("12.04637° S", fallback.formattedLat)
        assertEquals("77.04279° W", fallback.formattedLng)
        assertEquals("Cebu offline map not installed", fallback.statusMessage)
    }

    @Test
    fun downloadingState_preservesCoordinatesInFallback() {
        val sos = createSosMessage()
        val downloadingStatus = MapPackageStatus.Downloading(
            progressPercent = 45,
            bytesDownloaded = 45_000_000,
            totalBytes = 100_000_000
        )
        val state = resolveSosMapViewState(sos, downloadingStatus)

        assertTrue(state is SosMapViewState.MaplessFallback)
        val fallback = state as SosMapViewState.MaplessFallback
        assertEquals("Map download in progress", fallback.statusMessage)
        assertEquals(10.315696, fallback.lat, 0.000001)
    }

    @Test
    fun installedPackage_returnsMapReadyWithLocalFileAndAttribution() {
        val manifest = MapPackageManifest(
            packageId = "cebu-offline",
            version = 1,
            minLat = 9.40,
            minLng = 123.30,
            maxLat = 11.40,
            maxLng = 124.20,
            minZoom = 6,
            maxZoom = 15,
            byteLength = 1024L,
            sha256 = "test-hash",
            releaseDate = "2026-09-19",
            attribution = "© OpenStreetMap contributors, ODbL 1.0",
            assetUrl = "https://example.com/asset.zip"
        )

        val packageDir = File(tempDir, "cebu-v1").apply { mkdirs() }
        val pmtiles = File(packageDir, "cebu.pmtiles").apply { writeText("mock-pmtiles") }
        val style = File(packageDir, "style.json").apply { writeText("{\"version\": 8}") }

        val installedStatus = MapPackageStatus.Installed(
            manifest = manifest,
            packageDir = packageDir,
            pmtilesFile = pmtiles,
            styleFile = style
        )

        val sos = createSosMessage(lat = 10.315696, lng = 123.885437)
        val state = resolveSosMapViewState(sos, installedStatus)

        assertTrue(state is SosMapViewState.MapReady)
        val mapReady = state as SosMapViewState.MapReady
        assertEquals(10.315696, mapReady.lat, 0.000001)
        assertEquals(123.885437, mapReady.lng, 0.000001)
        assertEquals(style.absolutePath, mapReady.styleFile.absolutePath)
        // Ensure no network protocol used for style
        assertFalse(mapReady.styleFile.absolutePath.startsWith("http://"))
        assertFalse(mapReady.styleFile.absolutePath.startsWith("https://"))
        assertEquals("© OpenStreetMap contributors, ODbL 1.0", mapReady.attribution)
        assertEquals(1, mapReady.manifest.version)
    }

    @Test
    fun installedPackage_blankAttribution_defaultsToOsmAttribution() {
        val manifest = MapPackageManifest(
            packageId = "cebu-offline",
            version = 2,
            minLat = 9.40,
            minLng = 123.30,
            maxLat = 11.40,
            maxLng = 124.20,
            minZoom = 6,
            maxZoom = 15,
            byteLength = 1024L,
            sha256 = "test-hash",
            releaseDate = "2026-09-19",
            attribution = "", // Blank attribution in manifest
            assetUrl = "https://example.com/asset.zip"
        )

        val packageDir = File(tempDir, "cebu-v2").apply { mkdirs() }
        val pmtiles = File(packageDir, "cebu.pmtiles").apply { writeText("mock-pmtiles") }
        val style = File(packageDir, "style.json").apply { writeText("{\"version\": 8}") }

        val installedStatus = MapPackageStatus.Installed(
            manifest = manifest,
            packageDir = packageDir,
            pmtilesFile = pmtiles,
            styleFile = style
        )

        val sos = createSosMessage()
        val state = resolveSosMapViewState(sos, installedStatus)

        assertTrue(state is SosMapViewState.MapReady)
        val mapReady = state as SosMapViewState.MapReady
        assertEquals("© OpenStreetMap contributors", mapReady.attribution)
    }

    @Test
    fun storageGuardIntegration_fakePackageLifecycle_transitionsState() {
        // Initially no package installed
        val sos = createSosMessage()
        val initialState = resolveSosMapViewState(sos, storageGuard.getActivePackageStatus())
        assertTrue(initialState is SosMapViewState.MaplessFallback)

        // Install fake package through storage guard
        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "fake-pmtiles-content".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8}".toByteArray()
            )
        )
        val extractedDir = storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        assertNotNull(extractedDir)

        val manifest = MapPackageManifest(
            packageId = "cebu-test",
            version = 1,
            minLat = 9.40,
            minLng = 123.30,
            maxLat = 11.40,
            maxLng = 124.20,
            minZoom = 6,
            maxZoom = 15,
            byteLength = zipBytes.size.toLong(),
            sha256 = "dummy-sha",
            releaseDate = "2026-09-19",
            attribution = "ResQMesh Local Pilot",
            assetUrl = "https://example.com/test.zip"
        )
        val activated = storageGuard.activatePackage(manifest)
        assertTrue(activated)

        // Active status should now be Installed
        val activeStatus = storageGuard.getActivePackageStatus()
        assertTrue(activeStatus is MapPackageStatus.Installed)

        // ViewState should now resolve to MapReady
        val readyState = resolveSosMapViewState(sos, activeStatus)
        assertTrue(readyState is SosMapViewState.MapReady)
        val ready = readyState as SosMapViewState.MapReady
        assertEquals("ResQMesh Local Pilot", ready.attribution)
        assertTrue(ready.styleFile.exists())
    }
}
