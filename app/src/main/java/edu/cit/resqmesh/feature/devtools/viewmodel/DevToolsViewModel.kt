package edu.cit.resqmesh.feature.devtools.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cit.resqmesh.core.domain.usecase.MeshUseCases
import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.logging.TerminalLogEntry
import edu.cit.resqmesh.core.logging.trace.PacketTrace
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.core.logging.trace.TraceStore
import edu.cit.resqmesh.core.logging.trace.shortId
import edu.cit.resqmesh.feature.devtools.export.TraceExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Published copy of the trace rings. Packets are chronological (DATA and CONTROL merged by seq). */
data class DevToolsSnapshot(
    val packets: List<PacketTrace> = emptyList(),
    val voice: List<PacketTrace> = emptyList(),
    val errors: List<TerminalLogEntry> = emptyList(),
    val logs: List<TerminalLogEntry> = emptyList()
)

class DevToolsViewModel(useCases: MeshUseCases) : ViewModel() {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /**
     * Rebuilt at most every [POLL_MS] while someone collects, off the main thread, and only when the
     * store changed. Pausing freezes this published copy while the rings keep writing.
     */
    val snapshot: StateFlow<DevToolsSnapshot> = flow {
        var last = -1L
        while (true) {
            val changes = TraceStore.changeCount
            if (!TraceGate.enabled) {
                // Developer mode went off: drop the published copy and any pause.
                if (last != GATE_OFF) {
                    last = GATE_OFF
                    _paused.value = false
                    emit(DevToolsSnapshot())
                }
            } else if (!_paused.value && changes != last) {
                last = changes
                emit(build())
            }
            delay(POLL_MS)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DevToolsSnapshot())

    /** UI-only short node ID to display name. It is never passed to [TraceExporter]. */
    val names: StateFlow<Map<String, String>> = useCases.observeConnectedDevices()
        .map { devices ->
            buildMap {
                devices.forEach { d ->
                    val id = shortId(d.nodeId)
                    if (id != null && d.name.isNotBlank()) put(id, d.name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    fun setPaused(value: Boolean) {
        _paused.value = value
    }

    fun markErrorsSeen() = AppLogger.markErrorsSeen()

    /** Writes a sanitized export to [uri]; returns an error message, or null on success. */
    @Suppress("TooGenericExceptionCaught") // export must report any I/O failure instead of crashing
    suspend fun export(resolver: ContentResolver, uri: Uri, csv: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val packets = (TraceStore.dataSnapshot() + TraceStore.controlSnapshot() + TraceStore.voiceSnapshot())
                .sortedBy { it.seq }
            val logs = TraceStore.logsSnapshot()
            val errors = TraceStore.errorsSnapshot()
            val body = if (csv) TraceExporter.csv(packets, logs, errors) else TraceExporter.text(packets, logs, errors)
            val stream = resolver.openOutputStream(uri) ?: return@withContext "Could not open the file"
            stream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            null
        } catch (e: Exception) {
            "Export failed: ${e.javaClass.simpleName}"
        }
    }

    private fun build() = DevToolsSnapshot(
        packets = (TraceStore.dataSnapshot() + TraceStore.controlSnapshot()).sortedBy { it.seq },
        voice = TraceStore.voiceSnapshot(),
        errors = AppLogger.errorsSnapshot(),
        logs = AppLogger.logs.value
    )

    private companion object {
        const val POLL_MS = 250L
        const val GATE_OFF = -2L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
