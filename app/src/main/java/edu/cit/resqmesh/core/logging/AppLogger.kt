package edu.cit.resqmesh.core.logging

import android.util.Log
import edu.cit.resqmesh.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.delay
import edu.cit.resqmesh.core.logging.trace.LogTrace
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.core.logging.trace.TraceLevel
import edu.cit.resqmesh.core.logging.trace.TraceStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("TooManyFunctions")
object AppLogger {
    /** Guards only the formatted-log cache and date format, so reading logs never blocks writers. */
    private val logsLock = Any()
    private const val POLL_MS = 250L

    /**
     * Log rows live in the lock-free [TraceStore] ring; formatting and MAC redaction happen here,
     * at read time. Collectors poll the store's change counter and only rebuild when it moved.
     */
    val logs: StateFlow<List<TerminalLogEntry>> = object : StateFlow<List<TerminalLogEntry>> {
        private val cache = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
        private var cachedAt = -1L

        override val replayCache: List<List<TerminalLogEntry>> get() = listOf(value)

        override val value: List<TerminalLogEntry>
            get() = synchronized(logsLock) {
                val changes = TraceStore.changeCount
                if (changes != cachedAt) {
                    cachedAt = changes
                    cache.value = TraceStore.logsSnapshot().map(::toEntry)
                }
                cache.value
            }

        override suspend fun collect(collector: FlowCollector<List<TerminalLogEntry>>): Nothing {
            var last: List<TerminalLogEntry>? = null
            while (true) {
                val current = value
                if (current !== last) {
                    last = current
                    collector.emit(current)
                }
                delay(POLL_MS)
            }
        }
    }

    /** ERROR-level entries since the Errors view was last shown, for the badge dot. */
    val errorCount: StateFlow<Int> get() = TraceStore.errorCount

    fun markErrorsSeen() = TraceStore.markErrorsSeen()

    /** Redacted, formatted copy of the ERRORS ring (WARN and ERROR), oldest first. */
    fun errorsSnapshot(): List<TerminalLogEntry> = synchronized(logsLock) { TraceStore.errorsSnapshot().map(::toEntry) }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

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
        // Logcat output is debug-only; the in-app ring below is stored only while the trace gate is on.
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }

        if (!TraceGate.enabled) return
        TraceStore.addLog(
            LogTrace(
                seq = TraceStore.nextSeq(),
                epochMs = System.currentTimeMillis(),
                level = TraceLevel.entries[level.ordinal],
                categoryOrdinal = category.ordinal,
                event = event,
                message = message,
                tag = tag,
                peerName = peerName,
                endpoint = endpoint,
                linkGeneration = linkGeneration,
                role = role,
                isVerbose = isVerbose
            )
        )
    }

    private fun toEntry(t: LogTrace): TerminalLogEntry = TerminalLogEntry(
        sequence = t.seq,
        timestampLabel = timeFormat.format(Date(t.epochMs)),
        level = TerminalLogLevel.valueOf(t.level.name),
        category = TerminalLogCategory.entries[t.categoryOrdinal],
        event = t.event,
        message = redactEndpoints(t.message),
        tag = t.tag,
        peerName = t.peerName,
        endpoint = t.endpoint,
        linkGeneration = t.linkGeneration,
        role = t.role,
        isVerbose = t.isVerbose
    )

    fun clear() {
        TraceStore.clearLogs()
    }

    /** Non-reversible, stable name tag so logs can correlate a peer without exposing the name. */
    fun redactName(name: String?): String {
        if (name.isNullOrBlank()) return "<name:none>"
        val hash = Integer.toHexString(name.trim().lowercase().hashCode())
        return "<name:${name.trim().first()}…len=${name.trim().length}#$hash>"
    }

    // Every endpoint match contains ':', so messages without one cannot match the regex.
    private fun redactEndpoints(message: String): String =
        if (':' !in message) message
        else endpointRegex.replace(message) { match -> TerminalLogEntry.shortEndpoint(match.value) }

    private val endpointRegex = Regex("(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
}
