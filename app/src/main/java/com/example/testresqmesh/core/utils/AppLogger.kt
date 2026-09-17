package com.example.testresqmesh.core.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private val lock = Any()
    private val _logs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val logs: StateFlow<List<TerminalLogEntry>> = _logs.asStateFlow()
    private val _links = MutableStateFlow<List<TerminalLinkSnapshot>>(emptyList())
    val links: StateFlow<List<TerminalLinkSnapshot>> = _links.asStateFlow()
    private const val MAX_LOGS = 1_000
    private var nextSequence = 0L

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _isTerminalVisible = MutableStateFlow(false)
    val isTerminalVisible: StateFlow<Boolean> = _isTerminalVisible.asStateFlow()

    fun toggleTerminal() {
        _isTerminalVisible.value = !_isTerminalVisible.value
    }

    fun hideTerminal() {
        _isTerminalVisible.value = false
    }

    fun d(tag: String, message: String) {
        event(
            category = TerminalLogClassifier.categoryFor(tag, message),
            event = tag,
            message = message,
            level = TerminalLogClassifier.levelFor(message),
            tag = tag
        )
    }

    fun event(
        category: TerminalLogCategory,
        event: String,
        message: String,
        level: TerminalLogLevel = TerminalLogLevel.INFO,
        tag: String = "RESQMESH",
        peerName: String? = null,
        endpoint: String? = null,
        linkGeneration: Long? = null,
        role: String? = null,
        isVerbose: Boolean = false
    ) {
        // Preserve the existing Logcat contract: callers historically used AppLogger.d().
        Log.d(tag, message)

        synchronized(lock) {
            val entry = TerminalLogEntry(
                sequence = ++nextSequence,
                timestampLabel = timeFormat.format(Date()),
                level = level,
                category = category,
                event = event,
                message = redactEndpoints(message),
                tag = tag,
                peerName = peerName,
                endpoint = endpoint,
                linkGeneration = linkGeneration,
                role = role,
                isVerbose = isVerbose
            )
            _logs.value = (_logs.value + entry).takeLast(MAX_LOGS)
        }
    }

    /** Maintains the terminal's live direct-link summary without creating duplicate log rows. */
    fun updateLink(
        endpoint: String,
        peerName: String?,
        role: String,
        generation: Long,
        state: String,
        transport: String = "GATT"
    ) {
        synchronized(lock) {
            val keyMatches: (TerminalLinkSnapshot) -> Boolean = {
                it.endpoint == endpoint && it.role == role && it.generation == generation
            }
            val updated = TerminalLinkSnapshot(
                endpoint = endpoint,
                peerName = peerName?.takeUnless { it.isBlank() } ?: "Unknown",
                role = role,
                generation = generation,
                state = state,
                transport = transport
            )
            _links.value = _links.value.filterNot(keyMatches) + updated
        }
    }

    fun removeLink(endpoint: String, role: String, generation: Long) {
        synchronized(lock) {
            _links.value = _links.value.filterNot {
                it.endpoint == endpoint && it.role == role && it.generation == generation
            }
        }
    }

    fun updateLinkTransport(endpoint: String, transport: String) {
        synchronized(lock) {
            _links.value = _links.value.map { link ->
                if (link.endpoint == endpoint) link.copy(transport = transport) else link
            }
        }
    }

    fun clearLinks() {
        synchronized(lock) {
            _links.value = emptyList()
        }
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
    }

    private fun redactEndpoints(message: String): String =
        endpointRegex.replace(message) { match -> TerminalLogEntry.shortEndpoint(match.value) }

    private val endpointRegex = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
}
