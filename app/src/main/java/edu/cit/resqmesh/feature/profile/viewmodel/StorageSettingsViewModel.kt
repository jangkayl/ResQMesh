package edu.cit.resqmesh.feature.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cit.resqmesh.core.storage.AppStorageCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageSettingsUiState(
    val cacheBytes: Long? = null,
    val busy: Boolean = false,
    /** True from confirmation until Android kills the process, or until a failed wipe. */
    val resetting: Boolean = false,
    val ownSosActive: Boolean = false,
    val message: String? = null
)

class StorageSettingsViewModel(
    private val cleaner: AppStorageCleaner,
    ownSosActive: Flow<Boolean>,
    private val stopMesh: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow(StorageSettingsUiState())
    val state: StateFlow<StorageSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { ownSosActive.collect { active -> _state.update { it.copy(ownSosActive = active) } } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val bytes = withContext(io) { cleaner.cacheBytes() }
            _state.update { it.copy(cacheBytes = bytes) }
        }
    }

    fun clearCache() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val freed = withContext(io) { cleaner.clearCache() }
            val remaining = withContext(io) { cleaner.cacheBytes() }
            val message = "Cache cleared (${formatStorageBytes(freed)} freed)"
            _state.update { it.copy(busy = false, cacheBytes = remaining, message = message) }
        }
    }

    /** Refuses while this phone's own SOS is active, so peers are not left with an unresolvable alert. */
    fun resetApp() {
        val current = _state.value
        if (current.busy) return
        if (current.ownSosActive) {
            _state.update { it.copy(message = "End your active SOS before resetting the app.") }
            return
        }
        _state.update { it.copy(busy = true, resetting = true, message = null) }
        viewModelScope.launch {
            stopMesh()
            // Lets the "Resetting" dialog show, so the process exit reads as intended rather than a crash.
            delay(RESET_NOTICE_MS)
            val wiped = withContext(io) { cleaner.resetAppData() }
            if (!wiped) {
                _state.update { it.copy(busy = false, resetting = false, message = RESET_FAILED) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}

internal const val RESET_NOTICE_MS = 1_500L
private const val RESET_FAILED = "Reset failed. Use Android app settings to clear storage."
private const val KIB = 1024L
private const val MIB = KIB * KIB

internal fun formatStorageBytes(bytes: Long): String = when {
    bytes < KIB -> "$bytes B"
    bytes < MIB -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / KIB)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / MIB)
}
