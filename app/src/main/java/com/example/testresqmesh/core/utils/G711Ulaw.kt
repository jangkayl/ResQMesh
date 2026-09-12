package com.example.testresqmesh.core.utils

object G711Ulaw {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    private fun linearToUlaw(sample: Short): Byte {
        var pcm = sample.toInt()
        val sign = if (pcm < 0) 0x80 else 0
        if (pcm < 0) pcm = -pcm
        if (pcm > CLIP) pcm = CLIP
        pcm += BIAS

        val exponent = when {
            pcm >= 0x4000 -> 7
            pcm >= 0x2000 -> 6
            pcm >= 0x1000 -> 5
            pcm >= 0x0800 -> 4
            pcm >= 0x0400 -> 3
            pcm >= 0x0200 -> 2
            pcm >= 0x0100 -> 1
            else -> 0
        }

        val mantissa = (pcm shr (exponent + 3)) and 0x0F
        val ulaw = (sign or (exponent shl 4) or mantissa).inv()
        return ulaw.toByte()
    }

    private fun ulawToLinear(ulaw: Byte): Short {
        val u = ulaw.toInt().inv()
        val sign = u and 0x80
        val exponent = (u and 0x70) shr 4
        val mantissa = u and 0x0F

        var pcm = (mantissa shl 3) + 0x84
        pcm = pcm shl exponent
        pcm -= BIAS
        return if (sign != 0) (-pcm).toShort() else pcm.toShort()
    }

    fun compress(pcmData: ByteArray): ByteArray {
        val ulawData = ByteArray(pcmData.size / 2)
        var i = 0
        var j = 0
        while (i + 1 < pcmData.size) {
            val low = pcmData[i].toInt() and 0xFF
            val high = pcmData[i + 1].toInt() shl 8
            val sample = (low or high).toShort()
            ulawData[j] = linearToUlaw(sample)
            i += 2
            j++
        }
        return ulawData
    }

    fun decompress(ulawData: ByteArray, gainMultiplier: Float = 1.0f): ByteArray {
        val pcmData = ByteArray(ulawData.size * 2)
        var i = 0
        var j = 0
        while (i < ulawData.size) {
            val sample = ulawToLinear(ulawData[i])
            var amplified = (sample * gainMultiplier).toInt()
            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE.toInt()
            if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE.toInt()
            
            pcmData[j] = (amplified and 0xFF).toByte()
            pcmData[j + 1] = ((amplified shr 8) and 0xFF).toByte()
            i++
            j += 2
        }
        return pcmData
    }
}
