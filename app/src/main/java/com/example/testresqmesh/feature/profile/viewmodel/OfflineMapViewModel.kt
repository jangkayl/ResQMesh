package com.example.testresqmesh.feature.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testresqmesh.core.map.catalog.MapPackageCatalogEntry
import com.example.testresqmesh.core.map.download.ConnectivityProvider
import com.example.testresqmesh.core.map.download.MapPackageDownloader
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfflineMapUiState(
    val catalogEntry: MapPackageCatalogEntry = MapPackageCatalogEntry(),
    val packageStatus: MapPackageStatus = MapPackageStatus.NotInstalled,
    val isWifiConnected: Boolean = true,
    val requireWifi: Boolean = true,
    val availableStorageBytes: Long = 0L,
    val isDownloadingOrInstalling: Boolean = false,
    val wifiRequiredWarning: Boolean = false,
    val insufficientStorageError: Boolean = false,
    val corruptPackageError: Boolean = false,
    val errorMessage: String? = null,
    val isUpdateAvailable: Boolean = false,
    val activeManifest: MapPackageManifest? = null
)

class OfflineMapViewModel(
    private val storageGuard: MapStorageGuard,
    private val downloader: MapPackageDownloader,
    private val connectivityProvider: ConnectivityProvider,
    private val catalogEntry: MapPackageCatalogEntry = MapPackageCatalogEntry()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OfflineMapUiState(
            catalogEntry = catalogEntry,
            isWifiConnected = connectivityProvider.isWifiConnected(),
            availableStorageBytes = storageGuard.getAvailableBytes()
        )
    )
    val uiState: StateFlow<OfflineMapUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val currentStatus = storageGuard.getActivePackageStatus()
        val isWifi = connectivityProvider.isWifiConnected()
        val availableBytes = storageGuard.getAvailableBytes()

        val activeManifest = (currentStatus as? MapPackageStatus.Installed)?.manifest
        val isUpdate = activeManifest != null && catalogEntry.targetVersion > activeManifest.version

        _uiState.update { current ->
            current.copy(
                packageStatus = currentStatus,
                isWifiConnected = isWifi,
                availableStorageBytes = availableBytes,
                activeManifest = activeManifest,
                isUpdateAvailable = isUpdate,
                wifiRequiredWarning = false,
                insufficientStorageError = false,
                corruptPackageError = false,
                errorMessage = null,
                isDownloadingOrInstalling = false
            )
        }
    }

    fun startDownload(forceWifiBypass: Boolean = false) {
        if (_uiState.value.isDownloadingOrInstalling) return

        val isWifi = connectivityProvider.isWifiConnected()
        _uiState.update { it.copy(isWifiConnected = isWifi) }

        val requireWifi = _uiState.value.requireWifi && !forceWifiBypass
        if (requireWifi && !isWifi) {
            _uiState.update {
                it.copy(
                    wifiRequiredWarning = true,
                    errorMessage = "Wi-Fi connection required to download offline map."
                )
            }
            return
        }

        // Check storage before initiating pipeline
        val estimatedBytesNeeded = catalogEntry.estimatedDownloadSizeBytes * 2
        if (!storageGuard.hasSufficientStorage(estimatedBytesNeeded)) {
            _uiState.update {
                it.copy(
                    insufficientStorageError = true,
                    errorMessage = "Insufficient storage space. Need approximately ${(estimatedBytesNeeded / (1024 * 1024))} MB."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isDownloadingOrInstalling = true,
                wifiRequiredWarning = false,
                insufficientStorageError = false,
                corruptPackageError = false,
                errorMessage = null
            )
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.downloadAndInstallPackage(
                manifestUrl = catalogEntry.manifestUrl,
                signatureUrl = catalogEntry.signatureUrl,
                maintainerPublicKey = catalogEntry.maintainerPublicKey,
                requireWifi = requireWifi
            ).collect { status ->
                handleDownloadStatusEmission(status)
            }
        }
    }

    private fun handleDownloadStatusEmission(status: MapPackageStatus) {
        when (status) {
            is MapPackageStatus.Error -> {
                val isCorrupt = status.message.contains("checksum", ignoreCase = true) ||
                        status.message.contains("corrupted", ignoreCase = true) ||
                        status.message.contains("signature verification failed", ignoreCase = true)
                val isStorage = status.message.contains("storage", ignoreCase = true)
                val isWifi = status.message.contains("wi-fi", ignoreCase = true)

                _uiState.update { current ->
                    current.copy(
                        packageStatus = status,
                        isDownloadingOrInstalling = false,
                        corruptPackageError = isCorrupt,
                        insufficientStorageError = isStorage,
                        wifiRequiredWarning = isWifi,
                        errorMessage = status.message
                    )
                }
            }
            is MapPackageStatus.Installed -> {
                _uiState.update { current ->
                    current.copy(
                        packageStatus = status,
                        activeManifest = status.manifest,
                        isDownloadingOrInstalling = false,
                        isUpdateAvailable = false,
                        wifiRequiredWarning = false,
                        insufficientStorageError = false,
                        corruptPackageError = false,
                        errorMessage = null
                    )
                }
            }
            else -> {
                _uiState.update { current ->
                    current.copy(
                        packageStatus = status,
                        isDownloadingOrInstalling = true
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                wifiRequiredWarning = false,
                insufficientStorageError = false,
                corruptPackageError = false,
                errorMessage = null
            )
        }
        startDownload()
    }

    fun deletePackage() {
        downloadJob?.cancel()
        downloadJob = null
        val deleted = storageGuard.deleteActivePackage()
        if (deleted) {
            refreshStatus()
        } else {
            _uiState.update {
                it.copy(errorMessage = "Failed to delete offline map package.")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        storageGuard.cleanStaging()
        refreshStatus()
    }
}
