package edu.cit.resqmesh.core.network.bluetooth

import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import java.io.EOFException
import java.io.InputStream

/** Caps frames (and bytes) read off an L2CAP socket but not yet consumed on the handler thread. */
class L2capInboundGate(
    private val maxFrames: Int = MAX_PENDING_FRAMES,
    private val maxBytes: Long = MAX_PENDING_BYTES
) {
    private var frames = 0
    private var bytes = 0L

    /** True when the frame was admitted; the caller must [release] it exactly once. */
    @Synchronized fun tryAcquire(size: Int): Boolean {
        if (frames >= maxFrames || bytes + size > maxBytes) return false
        frames++
        bytes += size
        return true
    }

    @Synchronized fun release(size: Int) {
        if (frames > 0) frames--
        bytes = (bytes - size).coerceAtLeast(0L)
    }

    companion object {
        const val MAX_PENDING_FRAMES = 32
        const val MAX_PENDING_BYTES = 4L * 1024 * 1024
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
    private val onProgress: () -> Unit = {}
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
            onProgress()
        }
        return payload
    }

    /** Consumes and drops a payload (queue full) without buffering it; false when [isOwned] turned false. */
    fun skipPayload(length: Int, isOwned: () -> Boolean = { true }): Boolean {
        val scratch = ByteArray(minOf(length, IO_CHUNK_BYTES))
        var remaining = length
        while (remaining > 0) {
            val count = input.read(scratch, 0, minOf(scratch.size, remaining))
            if (count < 0) throw EOFException()
            if (!isOwned()) return false
            remaining -= count
            onProgress()
        }
        return true
    }

    companion object {
        const val IO_CHUNK_BYTES = 16 * 1024
        const val INITIAL_PAYLOAD_BYTES = 16 * 1024
        private const val BITS_PER_BYTE = 8
    }
}
