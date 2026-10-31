package edu.cit.resqmesh.core.audio

import edu.cit.resqmesh.core.audio.ImageQuality.HIGH
import edu.cit.resqmesh.core.audio.ImageQuality.STANDARD
import edu.cit.resqmesh.core.audio.ImageQuality.VERY_HIGH
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageEncodingPolicyTest {
    private val target = STANDARD.targetBytes

    @Test fun `credits stay proportional to target bytes at every level`() {
        ImageQuality.entries.forEach { level ->
            assertEquals(STANDARD.targetBytes * level.credits, level.targetBytes)
        }
    }

    @Test fun `very high fits 720 px and doubles the high target`() {
        assertEquals(720 to 540, ImageEncodingPolicy.scaledSize(1000, 750, VERY_HIGH))
        assertEquals(2, ImageEncodingPolicy.sampleSize(4000, 3000, VERY_HIGH))
        assertEquals(HIGH.targetBytes * 2, VERY_HIGH.targetBytes)
    }

    @Test fun `very high accepts bytes that high rejects`() {
        val tried = mutableListOf<Int>()
        ImageEncodingPolicy.encodeWithinTarget(VERY_HIGH) { quality ->
            tried += quality
            ByteArray(HIGH.targetBytes + 1)
        }
        assertEquals(listOf(60), tried)
    }

    @Test fun `stops at the first quality within target`() {
        val tried = mutableListOf<Int>()
        val result = ImageEncodingPolicy.encodeWithinTarget(STANDARD) { quality ->
            tried += quality
            ByteArray(if (quality > 40) target + 1 else target)
        }
        assertEquals(listOf(60, 50, 40), tried)
        assertEquals(target, result.size)
    }

    @Test fun `high accepts bytes that standard rejects`() {
        val tried = mutableListOf<Int>()
        ImageEncodingPolicy.encodeWithinTarget(HIGH) { quality -> tried += quality; ByteArray(target + 1) }
        assertEquals(listOf(60), tried)
    }

    @Test fun `top quality that fits skips lower rungs`() {
        val tried = mutableListOf<Int>()
        ImageEncodingPolicy.encodeWithinTarget(STANDARD) { quality -> tried += quality; ByteArray(1) }
        assertEquals(listOf(60), tried)
    }

    @Test fun `falls back to the lowest rung when nothing fits`() {
        val tried = mutableListOf<Int>()
        val result = ImageEncodingPolicy.encodeWithinTarget(STANDARD) { quality ->
            tried += quality
            ByteArray(target + quality)
        }
        assertEquals(ImageEncodingPolicy.QUALITY_LADDER, tried)
        assertEquals(target + 20, result.size)
    }

    @Test fun `sample size keeps twice the final long edge`() {
        assertEquals(4, ImageEncodingPolicy.sampleSize(4000, 3000, STANDARD))
        assertEquals(4, ImageEncodingPolicy.sampleSize(3000, 4000, STANDARD))
        assertEquals(4, ImageEncodingPolicy.sampleSize(4000, 3000, HIGH))
        assertEquals(2, ImageEncodingPolicy.sampleSize(3000, 2000, HIGH))
        assertEquals(1, ImageEncodingPolicy.sampleSize(1000, 800, STANDARD))
        assertEquals(1, ImageEncodingPolicy.sampleSize(0, 0, STANDARD))
    }

    @Test fun `scaling fits the long edge and keeps aspect ratio`() {
        assertEquals(320 to 240, ImageEncodingPolicy.scaledSize(1000, 750, STANDARD))
        assertEquals(240 to 320, ImageEncodingPolicy.scaledSize(750, 1000, STANDARD))
        assertEquals(480 to 360, ImageEncodingPolicy.scaledSize(1000, 750, HIGH))
    }

    @Test fun `small images are never upscaled`() {
        assertEquals(200 to 100, ImageEncodingPolicy.scaledSize(200, 100, HIGH))
    }

    @Test fun `extreme aspect ratios keep at least one pixel`() {
        assertEquals(320 to 1, ImageEncodingPolicy.scaledSize(10_000, 1, STANDARD))
    }
}
