package com.example.testresqmesh.core.network.ble

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object BlePacketSerializer {
    const val MAGIC_BYTE: Byte = 0x52 // 'R' for ResQMesh

    // Takes a full string payload and slices it into a list of 24-byte chunks
    fun serializeToChunks(msgId: String, payload: String, ttl: Int = 5): List<ByteArray> {
        val msgIdHash = (msgId.hashCode() and 0xFFFF)
        return serializeToChunksWithHash(msgIdHash, payload.toByteArray(StandardCharsets.UTF_8), ttl)
    }

    fun serializeToChunksWithHash(msgIdHashInt: Int, payloadBytes: ByteArray, ttl: Int = 5): List<ByteArray> {
        val msgIdHash = msgIdHashInt.toShort() // 2-byte hash
        
        // 31 byte limit - 7 bytes manuf overhead - 6 bytes header = 18 bytes
        // Using 16 bytes to be extremely safe against vendor-specific overheads
        val maxDataPerChunk = 16
        val chunks = mutableListOf<ByteArray>()
        
        val totalChunks = Math.ceil(payloadBytes.size.toDouble() / maxDataPerChunk).toInt()
        val actualTotal = if (totalChunks > 255) 255 else if (totalChunks == 0) 1 else totalChunks
        
        for (i in 0 until actualTotal) {
            val startIdx = i * maxDataPerChunk
            val endIdx = Math.min(startIdx + maxDataPerChunk, payloadBytes.size)
            val chunkDataSize = if (startIdx < payloadBytes.size) endIdx - startIdx else 0
            
            val buffer = ByteBuffer.allocate(6 + chunkDataSize)
            buffer.put(MAGIC_BYTE)
            buffer.putShort(msgIdHash)
            buffer.put(ttl.toByte())
            buffer.put(i.toByte())
            buffer.put(actualTotal.toByte())
            
            if (chunkDataSize > 0) {
                buffer.put(payloadBytes, startIdx, chunkDataSize)
            }
            chunks.add(buffer.array())
        }
        return chunks
    }

    // Returns a ParsedChunk
    fun deserializeChunk(data: ByteArray): ParsedChunk? {
        if (data.size < 6) return null
        
        val buffer = ByteBuffer.wrap(data)
        val magic = buffer.get()
        if (magic != MAGIC_BYTE) return null
        
        val msgIdHash = buffer.getShort()
        val ttl = buffer.get().toInt() and 0xFF
        val chunkIndex = buffer.get().toInt() and 0xFF
        val totalChunks = buffer.get().toInt() and 0xFF
        
        val payloadBytes = ByteArray(buffer.remaining())
        buffer.get(payloadBytes)
        
        return ParsedChunk(msgIdHash, ttl, chunkIndex, totalChunks, payloadBytes)
    }
}

data class ParsedChunk(
    val msgIdHash: Short,
    val ttl: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val data: ByteArray
)
