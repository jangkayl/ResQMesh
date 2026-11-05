package edu.cit.resqmesh.core.storage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppStorageCleanerTest {

    private lateinit var root: File
    private lateinit var cache: File
    private lateinit var files: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("cleaner").toFile()
        cache = File(root, "cache").apply { mkdirs() }
        files = File(root, "files").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun clearCacheDeletesNestedCacheFilesAndKeepsOtherStorage() {
        File(cache, "temp_audio_record.amr").writeBytes(ByteArray(100))
        File(cache, "captures").apply { mkdirs() }.resolve("shot.jpg").writeBytes(ByteArray(50))
        val map = File(files, "offline_maps/cebu.pmtiles").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }
        val cleaner = AppStorageCleaner(cache) { true }

        assertEquals(150L, cleaner.cacheBytes())
        assertEquals(150L, cleaner.clearCache())

        assertEquals(0L, cleaner.cacheBytes())
        assertTrue(cache.isDirectory)
        assertTrue(map.isFile)
    }

    @Test
    fun clearCacheOnEmptyDirectoryFreesNothing() {
        assertEquals(0L, AppStorageCleaner(cache) { true }.clearCache())
    }

    @Test
    fun resetReportsPlatformResult() {
        assertFalse(AppStorageCleaner(cache) { false }.resetAppData())
        assertTrue(AppStorageCleaner(cache) { true }.resetAppData())
    }
}
