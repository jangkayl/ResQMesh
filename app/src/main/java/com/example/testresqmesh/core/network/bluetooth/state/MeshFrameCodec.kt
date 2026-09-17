package com.example.testresqmesh.core.network.bluetooth.state

import java.nio.ByteBuffer

/** Shared length-prefix rules for GATT and L2CAP payloads. */
object MeshFrameCodec {
    const val MAX_PAYLOAD_BYTES = 10 * 1024 * 1024 - 1
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

    fun append(current: ByteArray, chunk: ByteArray): AppendResult {
        val combinedSize = current.size.toLong() + chunk.size.toLong()
        if (combinedSize > MAX_FRAME_BYTES) {
            return AppendResult.Rejected("buffer exceeds $MAX_FRAME_BYTES bytes")
        }
        val combined = ByteArray(combinedSize.toInt())
        current.copyInto(combined)
        chunk.copyInto(combined, destinationOffset = current.size)

        val payloads = mutableListOf<ByteArray>()
        var cursor = 0
        while (combined.size - cursor >= Int.SIZE_BYTES) {
            val expectedLength = ByteBuffer.wrap(combined, cursor, Int.SIZE_BYTES).int
            if (!isValidPayloadLength(expectedLength)) {
                return AppendResult.Rejected("invalid payload length $expectedLength")
            }
            val frameSize = Int.SIZE_BYTES.toLong() + expectedLength.toLong()
            if (frameSize > combined.size - cursor) break
            val payloadStart = cursor + Int.SIZE_BYTES
            payloads += combined.copyOfRange(payloadStart, payloadStart + expectedLength)
            cursor += frameSize.toInt()
        }
        return AppendResult.Accepted(payloads, combined.copyOfRange(cursor, combined.size))
    }
}
