package com.example.testresqmesh.core.network.ble

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

object CompactBinarySerializer {
    
    // Packet Types
    private const val TYPE_FALLBACK_JSON: Byte = 0x00
    private const val TYPE_COMPRESSED_PUBLIC_CHAT: Byte = 0x01
    
    fun compressJsonToBinary(jsonString: String): ByteArray {
        // Leave RAW baseline untouched
        if (jsonString.startsWith("RAW:")) {
            return jsonString.toByteArray(StandardCharsets.UTF_8)
        }
        
        try {
            val json = JSONObject(jsonString)
            val isPrivate = json.optBoolean("isPrivate", false)
            val isSystem = json.optBoolean("isSystem", false)
            
            if (!isPrivate && !isSystem) {
                val out = ByteArrayOutputStream()
                out.write(TYPE_COMPRESSED_PUBLIC_CHAT.toInt())
                
                // 1. UUID (16 bytes)
                val idStr = json.getString("id")
                val uuid = UUID.fromString(idStr)
                val bb = ByteBuffer.allocate(16)
                bb.putLong(uuid.mostSignificantBits)
                bb.putLong(uuid.leastSignificantBits)
                out.write(bb.array())
                
                // 2. Timestamp (8 bytes)
                val ts = json.getLong("timestamp")
                val tsBb = ByteBuffer.allocate(8)
                tsBb.putLong(ts)
                out.write(tsBb.array())
                
                // 3. Flags (1 byte)
                var flags = 0
                if (json.optBoolean("isSOS", false)) flags = flags or 1
                if (json.optBoolean("isSOSCancel", false)) flags = flags or 2
                out.write(flags)
                
                // 4. Sender Name (Length 1 byte + UTF8 Bytes)
                val senderStr = json.optString("senderName", "Unknown")
                val senderBytes = senderStr.toByteArray(StandardCharsets.UTF_8)
                out.write(senderBytes.size.toByte().toInt())
                out.write(senderBytes)
                
                // 5. Text (Length 2 bytes + UTF8 Bytes)
                val textStr = json.optString("text", "")
                val textBytes = textStr.toByteArray(StandardCharsets.UTF_8)
                val textLenBb = ByteBuffer.allocate(2)
                textLenBb.putShort(textBytes.size.toShort())
                out.write(textLenBb.array())
                out.write(textBytes)
                
                return out.toByteArray()
            } else {
                return fallback(jsonString)
            }
        } catch (e: Exception) {
            return fallback(jsonString)
        }
    }
    
    private fun fallback(jsonString: String): ByteArray {
        val bytes = jsonString.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(TYPE_FALLBACK_JSON.toInt())
        out.write(bytes)
        return out.toByteArray()
    }
    
    fun expandBinaryToJson(data: ByteArray): String {
        val rawStr = String(data, StandardCharsets.UTF_8)
        if (rawStr.startsWith("RAW:")) return rawStr
        
        if (data.isEmpty()) return "{}"
        
        val buffer = ByteBuffer.wrap(data)
        val packetType = buffer.get()
        
        if (packetType == TYPE_FALLBACK_JSON) {
            val jsonBytes = ByteArray(buffer.remaining())
            buffer.get(jsonBytes)
            return String(jsonBytes, StandardCharsets.UTF_8)
        } else if (packetType == TYPE_COMPRESSED_PUBLIC_CHAT) {
            try {
                val high = buffer.getLong()
                val low = buffer.getLong()
                val idStr = UUID(high, low).toString()
                
                val ts = buffer.getLong()
                
                val flags = buffer.get().toInt()
                val isSOS = (flags and 1) != 0
                val isSOSCancel = (flags and 2) != 0
                
                val senderLen = buffer.get().toInt() and 0xFF
                val senderBytes = ByteArray(senderLen)
                buffer.get(senderBytes)
                val senderStr = String(senderBytes, StandardCharsets.UTF_8)
                
                val textLen = buffer.getShort().toInt() and 0xFFFF
                val textBytes = ByteArray(textLen)
                buffer.get(textBytes)
                val textStr = String(textBytes, StandardCharsets.UTF_8)
                
                val json = JSONObject()
                json.put("id", idStr)
                json.put("timestamp", ts)
                json.put("senderName", senderStr)
                json.put("text", textStr)
                json.put("isPrivate", false)
                json.put("isSystem", false)
                if (isSOS) json.put("isSOS", true)
                if (isSOSCancel) json.put("isSOSCancel", true)
                
                return json.toString()
            } catch (e: Exception) {
                return "{}"
            }
        }
        
        return "{}"
    }
}
