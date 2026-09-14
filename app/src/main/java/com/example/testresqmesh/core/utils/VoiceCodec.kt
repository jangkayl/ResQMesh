package com.example.testresqmesh.core.utils

/**
 * Ultra-low latency G.711 u-law codec.
 * Completely bypasses Android MediaCodec, eliminating all CSD header bugs,
 * Xiaomi/Samsung crashes, and state synchronization issues.
 * Compresses 16-bit PCM to 8-bit u-law (50% compression), easily fitting in BLE bandwidth.
 */
class VoiceCodec {
    private var isEncoderStarted = false
    private var isDecoderStarted = false

    fun startEncoder() { isEncoderStarted = true }
    fun stopEncoder() { isEncoderStarted = false }
    
    fun startDecoder() { isDecoderStarted = true }
    fun stopDecoder() { isDecoderStarted = false }

    fun encodeChunk(pcmData: ByteArray): ByteArray? {
        if (!isEncoderStarted || pcmData.isEmpty()) return null
        val out = ByteArray(pcmData.size / 2)
        var outIdx = 0
        for (i in 0 until pcmData.size - 1 step 2) {
            val low = pcmData[i].toInt() and 0xFF
            val high = pcmData[i + 1].toInt() shl 8
            val pcm = (low or high).toShort()
            out[outIdx++] = linearToUlaw(pcm)
        }
        return out
    }

    fun decodeChunk(encodedData: ByteArray): ByteArray? {
        if (!isDecoderStarted || encodedData.isEmpty()) return null
        val out = ByteArray(encodedData.size * 2)
        var outIdx = 0
        for (i in encodedData.indices) {
            val pcm = ulawToLinear(encodedData[i])
            out[outIdx++] = (pcm.toInt() and 0xFF).toByte()
            out[outIdx++] = ((pcm.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun linearToUlaw(pcm: Short): Byte {
        val sign = if (pcm < 0) 0x80 else 0
        var mag = if (pcm < 0) -pcm else pcm.toInt()
        mag = mag + 132
        if (mag > 32767) mag = 32767
        var exp = 7
        for (i in 7 downTo 0) {
            if ((mag and (0x100 shl i)) != 0) {
                exp = i + 1
                break
            }
        }
        val mantissa = (mag shr (exp + 3)) and 0x0F
        val ulaw = (sign or (exp shl 4) or mantissa).inv()
        return ulaw.toByte()
    }

    private fun ulawToLinear(ulaw: Byte): Short {
        val ulawInv = ulaw.toInt().inv()
        val sign = if ((ulawInv and 0x80) != 0) -1 else 1
        val exp = (ulawInv shr 4) and 0x07
        val mantissa = ulawInv and 0x0F
        var mag = (mantissa shl 3) + 132
        mag = mag shl exp
        mag -= 132
        return (sign * mag).toShort()
    }
}
