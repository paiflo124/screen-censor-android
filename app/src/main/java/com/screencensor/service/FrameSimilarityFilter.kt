package com.screencensor.service

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Smart Frame Similarity & Motion Gate
 * Inspired by Bubble Screen Translate (com.niven.translator)
 *
 * Prevents thermal throttling, battery drain, and CPU saturation by:
 * 1. Skipping AI inference when screen is STATIC (reuses cached boxes - 0ms cost)
 * 2. Skipping AI inference when screen is SCROLLING FAST (waits for stable frame)
 * 3. Only triggering YOLO inference when screen has settled on new content
 */
class FrameSimilarityFilter(
    private val sampleWidth: Int = 48,
    private val sampleHeight: Int = 48
) {
    enum class FrameState {
        STATIC,      // Screen unchanged: reuse cached detections (0ms, 0% CPU)
        SCROLLING,   // Screen moving fast: skip AI, wait for stable frame
        CHANGED      // Screen has new stable content: run YOLO detection
    }

    private val previousSamples = IntArray(sampleWidth * sampleHeight)
    private var isFirstFrame = true
    private var consecutiveStaticFrames = 0
    private var consecutiveMovingFrames = 0

    // Downsampled pixel buffer to avoid allocations
    private val currentSamples = IntArray(sampleWidth * sampleHeight)

    /**
     * Evaluates whether the current frame warrants running heavy AI inference.
     * Samples pixels in a stride grid across the source bitmap.
     */
    fun evaluate(bitmap: Bitmap): FrameState {
        val bw = bitmap.width
        val bh = bitmap.height
        val stepX = bw / sampleWidth
        val stepY = bh / sampleHeight

        if (bw <= 0 || bh <= 0 || stepX <= 0 || stepY <= 0) {
            return FrameState.CHANGED
        }

        var sampleIdx = 0
        var totalDiff = 0L

        // Fast downsampled pixel sampling (under 0.2ms)
        for (y in 0 until sampleHeight) {
            val py = (y * stepY).coerceAtMost(bh - 1)
            for (x in 0 until sampleWidth) {
                val px = (x * stepX).coerceAtMost(bw - 1)
                val pixel = bitmap.getPixel(px, py)
                currentSamples[sampleIdx] = pixel

                if (!isFirstFrame) {
                    val prevPixel = previousSamples[sampleIdx]
                    // Fast RGB Manhattan distance
                    val dr = abs(((pixel shr 16) and 0xFF) - ((prevPixel shr 16) and 0xFF))
                    val dg = abs(((pixel shr 8) and 0xFF) - ((prevPixel shr 8) and 0xFF))
                    val db = abs((pixel and 0xFF) - (prevPixel and 0xFF))
                    totalDiff += (dr + dg + db)
                }
                sampleIdx++
            }
        }

        if (isFirstFrame) {
            System.arraycopy(currentSamples, 0, previousSamples, 0, sampleIdx)
            isFirstFrame = false
            return FrameState.CHANGED
        }

        // Average difference per pixel channel (0..255)
        val maxDiff = sampleWidth * sampleHeight * 3 * 255.0
        val diffRatio = totalDiff / maxDiff // 0.0 to 1.0

        // Copy current to previous for next iteration
        System.arraycopy(currentSamples, 0, previousSamples, 0, sampleIdx)

        return when {
            diffRatio < 0.015 -> { // Under 1.5% difference -> Static frame
                consecutiveStaticFrames++
                consecutiveMovingFrames = 0
                // Run a periodic refresh every 45 static frames (~2-3 sec) just in case
                if (consecutiveStaticFrames % 45 == 0) {
                    FrameState.CHANGED
                } else {
                    FrameState.STATIC
                }
            }
            diffRatio > 0.30 -> { // Over 30% difference -> Fast scrolling/flinging
                consecutiveMovingFrames++
                consecutiveStaticFrames = 0
                FrameState.SCROLLING
            }
            else -> { // Significant change, screen settling or steady video
                consecutiveStaticFrames = 0
                consecutiveMovingFrames = 0
                FrameState.CHANGED
            }
        }
    }

    fun reset() {
        isFirstFrame = true
        consecutiveStaticFrames = 0
        consecutiveMovingFrames = 0
    }
}
