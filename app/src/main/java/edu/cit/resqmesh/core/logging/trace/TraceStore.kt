package edu.cit.resqmesh.core.logging.trace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** In-memory event rings by stream, so a flood in one cannot evict another (e.g. LOGS vs ERRORS). */
@Suppress("TooManyFunctions")
object TraceStore {
    const val DATA_CAPACITY = 2000
    const val CONTROL_CAPACITY = 1000
    const val ERRORS_CAPACITY = 500
    const val LOGS_CAPACITY = 1000
    const val VOICE_CAPACITY = 300

    private val data = TraceRing<PacketTrace>(DATA_CAPACITY)
    private val control = TraceRing<PacketTrace>(CONTROL_CAPACITY)
    private val errors = TraceRing<LogTrace>(ERRORS_CAPACITY)
    private val logs = TraceRing<LogTrace>(LOGS_CAPACITY)
    private val voice = TraceRing<PacketTrace>(VOICE_CAPACITY)

    private val seq = AtomicLong(0)
    private val changes = AtomicLong(0)
    private val _errorCount = MutableStateFlow(0)

    /** Bumped on every write or clear; readers compare it to skip unchanged snapshots. */
    val changeCount: Long get() = changes.get()

    /** ERROR-level entries (not warnings) since the Errors view was last shown; drives the badge dot. */
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    fun nextSeq(): Long = seq.incrementAndGet()

    fun addPacket(trace: PacketTrace) {
        if (!TraceGate.enabled) return
        (if (trace.type.isControl) control else data).add(trace)
        changes.incrementAndGet()
    }

    fun addVoice(trace: PacketTrace) {
        if (!TraceGate.enabled) return
        voice.add(trace)
        changes.incrementAndGet()
    }

    fun addLog(entry: LogTrace) {
        if (!TraceGate.enabled) return
        logs.add(entry)
        if (entry.level >= TraceLevel.WARN) errors.add(entry)
        if (entry.level == TraceLevel.ERROR) _errorCount.update { it + 1 }
        changes.incrementAndGet()
    }

    fun markErrorsSeen() {
        _errorCount.value = 0
    }

    fun dataSnapshot(): List<PacketTrace> = data.snapshot()
    fun controlSnapshot(): List<PacketTrace> = control.snapshot()
    fun errorsSnapshot(): List<LogTrace> = errors.snapshot()
    fun logsSnapshot(): List<LogTrace> = logs.snapshot()
    fun voiceSnapshot(): List<PacketTrace> = voice.snapshot()

    /** Clears the LOGS ring only (terminal Clear); packet and error history stay. */
    fun clearLogs() {
        logs.clear()
        changes.incrementAndGet()
    }

    fun clear() {
        data.clear(); control.clear(); errors.clear(); logs.clear(); voice.clear()
        _errorCount.value = 0
        changes.incrementAndGet()
    }
}
