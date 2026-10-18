package edu.cit.resqmesh.core.network.bluetooth.state

import java.nio.ByteBuffer

/** Shared length-prefix rules for GATT and L2CAP payloads. */
object MeshFrameCodec {
    /**
     * 2 MiB media cap plus 64 KiB control headroom, matching OutboundQueuePolicy's largest single
     * transfer (a frame including its 4-byte prefix never exceeds this), so the sender cannot exceed it.
     */
    const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024 + 64 * 1024
    const val MAX_FRAME_BYTES = MAX_PAYLOAD_BYTES + Int.SIZE_BYTES
    const val MAX_PENDING_TRANSFERS = 128

    sealed interface AppendResult {
        data class Accepted(val payloads: List<ByteArray>, val remainder: ByteArray) : AppendResult
        data class Rejected(val reason: String) : AppendResult
    }

    fun encode(payload: ByteArray): ByteArray? {
        if (!isValidPayloadLength(payload.size)) return null
        return ByteBuffer.allocate(Int.SIZE_BYTES + payload.size)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun isValidPayloadLength(length: Int): Boolean = length in 1..MAX_PAYLOAD_BYTES

    /** Stateless convenience over [Reassembler]; prefer a long-lived [Reassembler] per link. */
    fun append(current: ByteArray, chunk: ByteArray): AppendResult {
        val reassembler = Reassembler()
        val result = reassembler.append(current + chunk)
        return if (result is AppendResult.Accepted) {
            AppendResult.Accepted(result.payloads, reassembler.pending())
        } else result
    }

    /**
     * Growable receive buffer: amortized-doubling growth, consumed bytes compacted in place, so total
     * work is linear in bytes received. Not thread-safe; callers use it on the BLE handler thread.
     */
    class Reassembler {
        private var buffer = ByteArray(INITIAL_CAPACITY)
        private var start = 0
        private var end = 0

        val size: Int get() = end - start
        fun isEmpty(): Boolean = size == 0
        fun clear() {
            buffer = ByteArray(INITIAL_CAPACITY)
            start = 0
            end = 0
        }

        fun pending(): ByteArray = buffer.copyOfRange(start, end)

        /** Accepted.remainder is always empty here; unconsumed bytes stay inside the reassembler. */
        fun append(chunk: ByteArray): AppendResult {
            val payloads = mutableListOf<ByteArray>()
            val failure = store(chunk) ?: drain(payloads)
            if (failure != null) clear()
            else if (start == end) {
                start = 0
                end = 0
                if (buffer.size > RETAINED_CAPACITY) buffer = ByteArray(INITIAL_CAPACITY)
            }
            return failure?.let { AppendResult.Rejected(it) } ?: AppendResult.Accepted(payloads, ByteArray(0))
        }

        /** Returns a rejection reason, or null when the chunk was buffered. */
        private fun store(chunk: ByteArray): String? {
            val tooBig = size.toLong() + chunk.size.toLong() > MAX_FRAME_BYTES
            if (!tooBig) {
                reserve(chunk.size)
                chunk.copyInto(buffer, destinationOffset = end)
                end += chunk.size
            }
            return if (tooBig) "buffer exceeds $MAX_FRAME_BYTES bytes" else null
        }

        /** Moves complete frames into [payloads]; returns a rejection reason for a bad length prefix. */
        private fun drain(payloads: MutableList<ByteArray>): String? {
            var failure: String? = null
            var complete = true
            while (complete && failure == null && size >= Int.SIZE_BYTES) {
                val expectedLength = ByteBuffer.wrap(buffer, start, Int.SIZE_BYTES).int
                val frameSize = Int.SIZE_BYTES.toLong() + expectedLength.toLong()
                when {
                    !isValidPayloadLength(expectedLength) -> failure = "invalid payload length $expectedLength"
                    frameSize > size -> complete = false
                    else -> {
                        val payloadStart = start + Int.SIZE_BYTES
                        payloads += buffer.copyOfRange(payloadStart, payloadStart + expectedLength)
                        start += frameSize.toInt()
                    }
                }
            }
            return failure
        }

        private fun reserve(extra: Int) {
            if (end + extra <= buffer.size) return
            if (start > 0) {
                buffer.copyInto(buffer, 0, start, end)
                end -= start
                start = 0
                if (end + extra <= buffer.size) return
            }
            var capacity = buffer.size.toLong()
            while (capacity < end.toLong() + extra) capacity *= 2
            buffer = buffer.copyOf(minOf(capacity, MAX_FRAME_BYTES.toLong()).toInt())
        }

        private companion object {
            const val INITIAL_CAPACITY = 512
            const val RETAINED_CAPACITY = 64 * 1024
        }
    }
}
