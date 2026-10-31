package edu.cit.resqmesh.core.audio

import kotlin.math.max
import kotlin.math.roundToInt

private const val BYTES_PER_KIB = 1024
private const val STANDARD_LONG_EDGE_PX = 320
private const val STANDARD_TARGET_KIB = 8
private const val HIGH_LONG_EDGE_PX = 480
private const val HIGH_TARGET_KIB = 16
private const val VERY_HIGH_LONG_EDGE_PX = 720
private const val VERY_HIGH_TARGET_KIB = 32
private const val VERY_HIGH_CREDITS = 4

/** Fixed send levels. [credits] is the image budget cost, kept proportional to [targetBytes]. */
enum class ImageQuality(val maxLongEdgePx: Int, val targetBytes: Int, val credits: Int) {
    STANDARD(STANDARD_LONG_EDGE_PX, STANDARD_TARGET_KIB * BYTES_PER_KIB, 1),
    HIGH(HIGH_LONG_EDGE_PX, HIGH_TARGET_KIB * BYTES_PER_KIB, 2),
    VERY_HIGH(VERY_HIGH_LONG_EDGE_PX, VERY_HIGH_TARGET_KIB * BYTES_PER_KIB, VERY_HIGH_CREDITS)
}

/** Sizing rules for outgoing images. Wire bytes set the send delay, so the byte target bounds it. */
object ImageEncodingPolicy {
    private const val TOP_QUALITY = 60
    private const val LOWEST_QUALITY = 20
    private const val QUALITY_STEP = 10
    val QUALITY_LADDER = (TOP_QUALITY downTo LOWEST_QUALITY step QUALITY_STEP).toList()

    /** Largest power-of-two sample size that still leaves twice the final long edge for a clean downscale. */
    fun sampleSize(width: Int, height: Int, level: ImageQuality): Int {
        val longEdge = max(width, height)
        var sample = 1
        while (longEdge / (sample * 2) >= level.maxLongEdgePx * 2) sample *= 2
        return sample
    }

    /** Fits the long edge to the level's limit and never upscales. */
    fun scaledSize(width: Int, height: Int, level: ImageQuality): Pair<Int, Int> {
        val longEdge = max(width, height)
        if (longEdge <= level.maxLongEdgePx) return width to height
        val ratio = level.maxLongEdgePx.toFloat() / longEdge
        return (width * ratio).roundToInt().coerceAtLeast(1) to (height * ratio).roundToInt().coerceAtLeast(1)
    }

    /** Returns the best encoder quality within the level's target, or the lowest rung when none fits. */
    fun encodeWithinTarget(level: ImageQuality, encode: (quality: Int) -> ByteArray): ByteArray {
        var encoded = ByteArray(0)
        for (quality in QUALITY_LADDER) {
            encoded = encode(quality)
            if (encoded.size <= level.targetBytes) break
        }
        return encoded
    }
}
