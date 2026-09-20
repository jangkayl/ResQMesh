package com.example.testresqmesh.core.map

import com.example.testresqmesh.core.map.download.ConnectivityProvider
import com.example.testresqmesh.core.map.download.HttpTransport
import com.example.testresqmesh.core.map.download.MapPackageDownloader
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.map.storage.MapStyleValidator
import com.example.testresqmesh.core.map.verifier.ManifestVerifier
import com.example.testresqmesh.core.map.verifier.SignatureVerifier
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MapPackageTest {

    private lateinit var tempDir: File
    private lateinit var storageGuard: MapStorageGuard

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("offline_map_test").toFile()
        storageGuard = MapStorageGuard(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // Helper: Create a zip archive with given files
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

    @Test
    fun noInstalledMapFallback_returnsNotInstalled() {
        val status = storageGuard.getActivePackageStatus()
        assertEquals(MapPackageStatus.NotInstalled, status)
    }

    @Test
    fun invalidSignatureOrHash_isRejected() {
        var signatureValid = true
        val fakeVerifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
                    return signatureValid
                }
            }
        )

        val sampleManifest = "{\"packageId\":\"cebu-city\",\"version\":1}".toByteArray()
        val sampleSignature = byteArrayOf(1, 2, 3)
        val sampleKey = byteArrayOf(4, 5, 6)

        // Valid signature case
        signatureValid = true
        assertTrue(fakeVerifier.verifyManifestSignature(sampleManifest, sampleSignature, sampleKey))

        // Tampered / invalid signature case
        signatureValid = false
        assertFalse(fakeVerifier.verifyManifestSignature(sampleManifest, sampleSignature, sampleKey))

        // Empty bytes case
        assertFalse(fakeVerifier.verifyManifestSignature(byteArrayOf(), sampleSignature, sampleKey))

        // Hash verification
        val testFile = File(tempDir, "test.zip").apply { writeBytes("valid-content".toByteArray()) }
        val correctHash = sha256Hex("valid-content".toByteArray())
        assertTrue(fakeVerifier.verifyPackageHash(testFile, correctHash))
        assertFalse(fakeVerifier.verifyPackageHash(testFile, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun ecdsaRealSignatureVerification_succeeds() {
        val keyPairGen = java.security.KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()

        val manifestData = "{\"packageId\":\"cebu-offline\",\"version\":1}".toByteArray()
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(manifestData)
        val signature = signer.sign()

        val realVerifier = ManifestVerifier() // Uses CompositeSignatureVerifier
        assertTrue(realVerifier.verifyManifestSignature(manifestData, signature, keyPair.public.encoded))

        // Tampered data should fail
        val tamperedData = "{\"packageId\":\"cebu-offline\",\"version\":2}".toByteArray()
        assertFalse(realVerifier.verifyManifestSignature(tamperedData, signature, keyPair.public.encoded))
    }

    @Test
    fun insufficientStorage_abortsBeforeDownload() = runBlocking {
        // Storage guard reporting only 10 MB free
        val lowStorageGuard = MapStorageGuard(
            baseDir = tempDir,
            availableBytesProvider = { 10L * 1024 * 1024 }
        )

        val verifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val manifest = MapPackageManifest(
            packageId = "cebu-city",
            version = 1,
            minLat = 10.2, minLng = 123.8, maxLat = 10.4, maxLng = 124.0,
            minZoom = 10, maxZoom = 16,
            byteLength = 100L * 1024 * 1024, // 100 MB package
            sha256 = "abc",
            releaseDate = "2026-09-19",
            attribution = "© OpenStreetMap contributors",
            assetUrl = "https://example.com/cebu-v1.zip"
        )

        val transport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray? = manifest.toJson().toByteArray()
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ) = false
        }

        val downloader = MapPackageDownloader(
            storageGuard = lowStorageGuard,
            verifier = verifier,
            connectivityProvider = object : ConnectivityProvider { override fun isWifiConnected() = true },
            httpTransport = transport
        )

        val statuses = downloader.downloadAndInstallPackage(
            manifestUrl = "https://example.com/manifest.json",
            signatureUrl = "https://example.com/manifest.sig",
            maintainerPublicKey = byteArrayOf(1, 2, 3),
            requireWifi = false
        ).toList()

        val lastStatus = statuses.last()
        assertTrue("Expected Error status, got $lastStatus", lastStatus is MapPackageStatus.Error)
        assertTrue((lastStatus as MapPackageStatus.Error).message.contains("Insufficient storage"))
        assertEquals(MapPackageStatus.NotInstalled, lowStorageGuard.getActivePackageStatus())
    }

    @Test
    fun downloadInterruption_preservesPartFileAndDoesNotCorruptState() = runBlocking {
        val verifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val manifest = MapPackageManifest(
            packageId = "cebu-city",
            version = 1,
            minLat = 10.2, minLng = 123.8, maxLat = 10.4, maxLng = 124.0,
            minZoom = 10, maxZoom = 16,
            byteLength = 1000L,
            sha256 = "hash123",
            releaseDate = "2026-09-19",
            attribution = "© OpenStreetMap contributors",
            assetUrl = "https://example.com/cebu-v1.zip"
        )

        // Transport that fails midway
        val transport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray? = manifest.toJson().toByteArray()
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ): Boolean {
                // Write partial bytes and fail
                destinationFile.parentFile?.mkdirs()
                destinationFile.writeBytes("partial-data".toByteArray())
                return false
            }
        }

        val downloader = MapPackageDownloader(
            storageGuard = storageGuard,
            verifier = verifier,
            connectivityProvider = object : ConnectivityProvider { override fun isWifiConnected() = true },
            httpTransport = transport
        )

        val statuses = downloader.downloadAndInstallPackage(
            manifestUrl = "https://example.com/manifest.json",
            signatureUrl = "https://example.com/manifest.sig",
            maintainerPublicKey = byteArrayOf(1, 2, 3),
            requireWifi = false
        ).toList()

        val lastStatus = statuses.last()
        assertTrue("Expected Error status, got $lastStatus", lastStatus is MapPackageStatus.Error)
        assertTrue((lastStatus as MapPackageStatus.Error).message.contains("interrupted"))

        // Active status remains NotInstalled
        assertEquals(MapPackageStatus.NotInstalled, storageGuard.getActivePackageStatus())

        // The partial staging file is safely retained
        val stagingPart = storageGuard.getStagingFile("cebu-v1.zip")
        assertTrue(stagingPart.exists())
        assertEquals("partial-data", stagingPart.readText())
    }

    @Test
    fun updateRollback_preservesExistingPackageWhenUpdateFails() = runBlocking {
        // 1. Setup existing valid v1 package
        val v1ZipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "pmtiles-v1".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to "{\"version\": 8}".toByteArray()
            )
        )
        val v1Hash = sha256Hex(v1ZipBytes)
        val v1Manifest = MapPackageManifest(
            packageId = "cebu-city",
            version = 1,
            minLat = 10.2, minLng = 123.8, maxLat = 10.4, maxLng = 124.0,
            minZoom = 10, maxZoom = 16,
            byteLength = v1ZipBytes.size.toLong(),
            sha256 = v1Hash,
            releaseDate = "2026-09-01",
            attribution = "© OpenStreetMap contributors",
            assetUrl = "https://example.com/cebu-v1.zip"
        )

        // Install v1 directly
        val v1Dir = storageGuard.extractPackageZip(v1ZipBytes.inputStream(), 1)
        assertNotNull(v1Dir)
        assertTrue(storageGuard.activatePackage(v1Manifest))

        val initialStatus = storageGuard.getActivePackageStatus()
        assertTrue(initialStatus is MapPackageStatus.Installed)
        assertEquals(1, (initialStatus as MapPackageStatus.Installed).manifest.version)

        // 2. Prepare v2 update that fails hash verification (corrupt payload)
        val v2CorruptBytes = "corrupt-v2-bytes".toByteArray()
        val v2Manifest = MapPackageManifest(
            packageId = "cebu-city",
            version = 2,
            minLat = 10.2, minLng = 123.8, maxLat = 10.4, maxLng = 124.0,
            minZoom = 10, maxZoom = 16,
            byteLength = v2CorruptBytes.size.toLong(),
            sha256 = "expected-different-sha256",
            releaseDate = "2026-09-19",
            attribution = "© OpenStreetMap contributors",
            assetUrl = "https://example.com/cebu-v2.zip"
        )

        val verifier = ManifestVerifier(
            signatureVerifier = object : SignatureVerifier {
                override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray) = true
            }
        )

        val transport = object : HttpTransport {
            override suspend fun fetchBytes(urlString: String): ByteArray? = v2Manifest.toJson().toByteArray()
            override suspend fun downloadToFile(
                urlString: String,
                destinationFile: File,
                resumeOffset: Long,
                onProgress: (Long, Long) -> Unit
            ): Boolean {
                destinationFile.parentFile?.mkdirs()
                destinationFile.writeBytes(v2CorruptBytes)
                return true
            }
        }

        val downloader = MapPackageDownloader(
            storageGuard = storageGuard,
            verifier = verifier,
            connectivityProvider = object : ConnectivityProvider { override fun isWifiConnected() = true },
            httpTransport = transport
        )

        val statuses = downloader.downloadAndInstallPackage(
            manifestUrl = "https://example.com/manifest-v2.json",
            signatureUrl = "https://example.com/manifest-v2.sig",
            maintainerPublicKey = byteArrayOf(1, 2, 3),
            requireWifi = false
        ).toList()

        val lastStatus = statuses.last()
        assertTrue("Expected Error status, got $lastStatus", lastStatus is MapPackageStatus.Error)
        val errorStatus = lastStatus as MapPackageStatus.Error
        assertTrue(errorStatus.message.contains("checksum verification failed"))

        // CRITICAL CHECK: Previous valid v1 package was preserved!
        assertNotNull(errorStatus.lastValidStatus)
        assertTrue(errorStatus.lastValidStatus is MapPackageStatus.Installed)
        assertEquals(1, (errorStatus.lastValidStatus as MapPackageStatus.Installed).manifest.version)

        val currentActive = storageGuard.getActivePackageStatus()
        assertTrue(currentActive is MapPackageStatus.Installed)
        assertEquals(1, (currentActive as MapPackageStatus.Installed).manifest.version)
        assertEquals("pmtiles-v1", (currentActive as MapPackageStatus.Installed).pmtilesFile.readText())
    }

    @Test
    fun extraction_rejectsStyleWithHttpOrHttpsNetworkUrls() {
        val httpStyle = """
            {
              "version": 8,
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"]
                }
              }
            }
        """.trimIndent()

        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "dummy-pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to httpStyle.toByteArray()
            )
        )

        val extracted = storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        assertNull("Package with network URLs in style must be rejected", extracted)
        assertFalse(File(storageGuard.packagesDir, "cebu-v1").exists())
    }

    @Test
    fun extraction_rejectsStyleWithMissingSpriteAssets() {
        val styleWithSprite = """
            {
              "version": 8,
              "sprite": "sprites/sprite",
              "sources": {
                "cebu": {
                  "type": "vector",
                  "url": "pmtiles://cebu.pmtiles"
                }
              }
            }
        """.trimIndent()

        // Missing sprite.png (only sprite.json provided)
        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "dummy-pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to styleWithSprite.toByteArray(),
                "sprites/sprite.json" to "{}".toByteArray()
            )
        )

        val extracted = storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        assertNull("Package with missing sprite PNG must be rejected", extracted)
        assertFalse(File(storageGuard.packagesDir, "cebu-v1").exists())
    }

    @Test
    fun extraction_rejectsStyleWithMissingGlyphsDir() {
        val styleWithGlyphs = """
            {
              "version": 8,
              "glyphs": "glyphs/{fontstack}/{range}.pbf",
              "sources": {
                "cebu": {
                  "type": "vector",
                  "url": "pmtiles://cebu.pmtiles"
                }
              }
            }
        """.trimIndent()

        // Glyphs declared but no glyphs/ directory or .pbf files in zip
        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "dummy-pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to styleWithGlyphs.toByteArray()
            )
        )

        val extracted = storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        assertNull("Package with missing glyphs directory must be rejected", extracted)
        assertFalse(File(storageGuard.packagesDir, "cebu-v1").exists())
    }

    @Test
    fun extraction_succeedsWhenAllLocalDependenciesArePresent() {
        val validSelfContainedStyle = """
            {
              "version": 8,
              "sprite": "sprites/sprite",
              "glyphs": "glyphs/{fontstack}/{range}.pbf",
              "sources": {
                "cebu": {
                  "type": "vector",
                  "url": "pmtiles://cebu.pmtiles"
                }
              }
            }
        """.trimIndent()

        val zipBytes = createZipArchive(
            mapOf(
                MapStorageGuard.PMTILES_FILENAME to "valid-pmtiles".toByteArray(),
                MapStorageGuard.STYLE_FILENAME to validSelfContainedStyle.toByteArray(),
                "sprites/sprite.json" to "{}".toByteArray(),
                "sprites/sprite.png" to byteArrayOf(1, 2, 3),
                "glyphs/Noto Sans Regular/0-255.pbf" to byteArrayOf(4, 5, 6),
                "LICENSE.txt" to "OpenStreetMap ODbL 1.0".toByteArray()
            )
        )

        val extracted = storageGuard.extractPackageZip(zipBytes.inputStream(), version = 1)
        assertNotNull("Package with all local dependencies must be accepted", extracted)
        assertTrue(File(extracted, MapStorageGuard.PMTILES_FILENAME).exists())
        assertTrue(File(extracted, MapStorageGuard.STYLE_FILENAME).exists())
        assertTrue(File(extracted, "sprites/sprite.json").exists())
        assertTrue(File(extracted, "sprites/sprite.png").exists())
        assertTrue(File(extracted, "glyphs/Noto Sans Regular/0-255.pbf").exists())
        assertTrue(File(extracted, "LICENSE.txt").exists())
    }

    @Test
    fun mapStyleValidator_resolveStyleForRuntime_rewritesRelativePaths() {
        val packageDir = File(tempDir, "cebu-v1").apply { mkdirs() }
        val styleFile = File(packageDir, "style.json").apply {
            writeText(
                """
                {
                  "version": 8,
                  "sprite": "sprites/sprite",
                  "glyphs": "glyphs/{fontstack}/{range}.pbf",
                  "sources": {
                    "cebu": {
                      "type": "vector",
                      "url": "pmtiles://cebu.pmtiles"
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val resolved = MapStyleValidator.resolveStyleForRuntime(packageDir, styleFile)
        val normalizedExpectedPath = packageDir.canonicalPath.replace('\\', '/')

        assertTrue("Resolved sprite must start with file://", resolved.contains("\"sprite\":\"file://$normalizedExpectedPath/sprites/sprite\""))
        assertTrue("Resolved glyphs must start with file://", resolved.contains("\"glyphs\":\"file://$normalizedExpectedPath/glyphs/{fontstack}/{range}.pbf\""))
        assertTrue("Resolved PMTiles source must use pmtiles://file://", resolved.contains("\"url\":\"pmtiles://file://$normalizedExpectedPath/cebu.pmtiles\""))
    }
}
