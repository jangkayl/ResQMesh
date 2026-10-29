package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import java.io.EOFException
import java.io.InputStream

/**
 * Caps frames (and bytes) read off an L2CAP socket but not yet consumed on the handler thread.
 * A full gate is back-pressure, not a drop: the reader thread waits in [awaitAcquire] before reading
 * the next frame body, so the peer's stream simply stalls instead of losing frames.
 */
class L2capInboundGate(
    private val maxFrames: Int = MAX_PENDING_FRAMES,
    private val maxBytes: Long = MAX_PENDING_BYTES
) {
    enum class Admission { ADMITTED, TIMED_OUT, CLOSED }

    private val lock = Object()
    private var frames = 0
    private var bytes = 0L
    private var closed = false

    /** True when the frame was admitted; the caller must [release] it exactly once. */
    fun tryAcquire(size: Int): Boolean = synchronized(lock) { admitLocked(size) }

    /**
     * Waits (on the calling reader thread only) up to [timeoutMs] for room. An empty queue always
     * admits, so one frame larger than the byte cap cannot deadlock. [isOpen] is re-checked every
     * [POLL_MS] so link retirement unblocks the wait even when [close] is not called.
     */
    fun awaitAcquire(size: Int, timeoutMs: Long, isOpen: () -> Boolean = { true }): Admission {
        val deadline = System.nanoTime() + timeoutMs * NANOS_PER_MS
        synchronized(lock) {
            var result: Admission? = null
            while (result == null) {
                val remainingMs = (deadline - System.nanoTime()) / NANOS_PER_MS
                result = when {
                    closed || !isOpen() -> Admission.CLOSED
                    admitLocked(size) -> Admission.ADMITTED
                    remainingMs <= 0 -> Admission.TIMED_OUT
                    else -> { lock.wait(minOf(remainingMs, POLL_MS)); null }
                }
            }
            return result
        }
    }

    fun release(size: Int) {
        synchronized(lock) {
            if (frames > 0) frames--
            bytes = (bytes - size).coerceAtLeast(0L)
            lock.notifyAll()
        }
    }

    /** Unblocks a waiting reader; later waits return [Admission.CLOSED]. */
    fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
    }

    private fun admitLocked(size: Int): Boolean {
        if (frames > 0 && (frames >= maxFrames || bytes + size > maxBytes)) return false
        frames++
        bytes += size
        return true
    }

    companion object {
        const val MAX_PENDING_FRAMES = 32
        const val MAX_PENDING_BYTES = 4L * 1024 * 1024
        const val STALL_TIMEOUT_MS = 15_000L
        private const val POLL_MS = 250L
        private const val NANOS_PER_MS = 1_000_000L
    }
}

/** Detects a partly received frame that stopped making progress. Idle between frames is not a stall. */
class L2capReadWatchdog(private val timeoutMs: Long = READ_TIMEOUT_MS) {
    @Volatile private var inFrame = false
    @Volatile private var lastProgressAt = 0L

    fun frameStarted(now: Long) { lastProgressAt = now; inFrame = true }
    fun progress(now: Long) { lastProgressAt = now }
    fun frameEnded() { inFrame = false }
    fun isStalled(now: Long): Boolean = inFrame && now - lastProgressAt > timeoutMs

    companion object {
        const val READ_TIMEOUT_MS = 15_000L
        const val CHECK_INTERVAL_MS = 5_000L
    }
}

/**
 * Length-prefixed frame reader that never allocates the claimed length up front: the payload buffer
 * grows by doubling as bytes arrive, so a peer claiming a large frame must actually send it.
 */
class L2capFrameReader(
    private val input: InputStream,
    private val onFrameStarted: () -> Unit = {},
    private val onProgress: () -> Unit = {},
    /** Called with the size of every raw read; may block to apply back-pressure. */
    private val onChunk: (Int) -> Unit = {}
) {
    sealed interface Header {
        data class Length(val value: Int) : Header
        data class Invalid(val value: Int) : Header
    }

    /** Blocks (idle is allowed) for the first header byte, then treats the frame as in progress. */
    fun readHeader(): Header {
        val first = input.read()
        if (first < 0) throw EOFException()
        onFrameStarted()
        var length = first
        repeat(Int.SIZE_BYTES - 1) {
            val next = input.read()
            if (next < 0) throw EOFException()
            length = (length shl BITS_PER_BYTE) or next
        }
        onProgress()
        return if (MeshFrameCodec.isValidPayloadLength(length)) Header.Length(length) else Header.Invalid(length)
    }

    /** Returns null when [isOwned] turns false mid-frame. */
    fun readPayload(length: Int, isOwned: () -> Boolean = { true }): ByteArray? {
        var payload = ByteArray(minOf(length, INITIAL_PAYLOAD_BYTES))
        var offset = 0
        while (offset < length) {
            if (offset == payload.size) payload = payload.copyOf(minOf(length.toLong(), payload.size * 2L).toInt())
            val count = input.read(payload, offset, minOf(IO_CHUNK_BYTES, payload.size - offset))
            if (count < 0) throw EOFException()
            if (!isOwned()) return null
            offset += count
            onChunk(count)
            onProgress()
        }
        return payload
    }

    companion object {
        const val IO_CHUNK_BYTES = 16 * 1024
        const val INITIAL_PAYLOAD_BYTES = 16 * 1024
        private const val BITS_PER_BYTE = 8
    }
}
