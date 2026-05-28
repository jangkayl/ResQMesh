package com.example.testresqmesh.feature.sos.utils

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import java.io.File

class MapDownloadManager(private val context: Context) {

    enum class DownloadState {
        IDLE, DOWNLOADING, COMPLETE, ERROR
    }

    private val _downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _totalTiles = MutableStateFlow(0)
    val totalTiles: StateFlow<Int> = _totalTiles.asStateFlow()

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val PREF_IS_DOWNLOADED = "is_cebu_map_downloaded"

    fun isMapDownloaded(): Boolean {
        return prefs.getBoolean(PREF_IS_DOWNLOADED, false)
    }

    fun markMapAsDownloaded() {
        prefs.edit().putBoolean(PREF_IS_DOWNLOADED, true).apply()
        _downloadState.value = DownloadState.COMPLETE
    }

    fun startCebuDownload() {
        if (_downloadState.value == DownloadState.DOWNLOADING) return

        _downloadState.value = DownloadState.DOWNLOADING
        _progress.value = 0
        _totalTiles.value = 0

        // Configure internal storage paths to avoid SecurityException crashes on newer Androids
        val basePath = File(context.filesDir, "osmdroid")
        basePath.mkdirs()
        val cachePath = File(basePath, "tiles")
        cachePath.mkdirs()

        Configuration.getInstance().apply {
            load(context, prefs)
            userAgentValue = context.packageName
            osmdroidBasePath = basePath
            osmdroidTileCache = cachePath
        }

        // Cebu City Approximate Bounding Box (N, E, S, W)
        val cebuBox = BoundingBox(10.38, 123.98, 10.25, 123.85)
        
        // Use XYTileSource to bypass osmdroid's hardcoded MAPNIK bulk download PolicyException crash
        val customTileSource = XYTileSource(
            "OpenStreetMap",
            0, 19, 256, ".png", arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            )
        )

        val tileProvider = MapTileProviderBasic(context)
        tileProvider.tileSource = customTileSource
        val tileWriter = SqlTileWriter()

        val cacheManager = CacheManager(tileProvider, tileWriter, 13, 17)

        // Download Zoom Levels 13 to 17 without osmdroid's internal crash-prone ProgressDialog
        cacheManager.downloadAreaAsyncNoUI(context, cebuBox, 13, 17, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                Log.d("MapDownloadManager", "Offline Map Download Complete!")
                markMapAsDownloaded()
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                // 'progress' is the number of tiles downloaded so far
                _progress.value = progress
            }

            override fun downloadStarted() {
                Log.d("MapDownloadManager", "Download Started")
            }

            override fun setPossibleTilesInArea(total: Int) {
                _totalTiles.value = total
                Log.d("MapDownloadManager", "Total Tiles to download: $total")
            }

            override fun onTaskFailed(errors: Int) {
                Log.e("MapDownloadManager", "Download Failed with $errors errors")
                _downloadState.value = DownloadState.ERROR
            }
        })
    }
}
