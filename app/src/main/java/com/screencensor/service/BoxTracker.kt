package com.screencensor.service

import android.graphics.RectF
import com.screencensor.model.DetectionBox
import kotlin.math.max
import kotlin.math.min

class BoxTracker(
    private val maxPersistenceFrames: Int = 2,
    private val smoothingFactor: Float = 0.65f // Lerp weight for new frame
) {
    private val trackedBoxes = mutableListOf<DetectionBox>()

    @Synchronized
    fun update(newDetections: List<DetectionBox>, smoothTrackingEnabled: Boolean): List<DetectionBox> {
        if (!smoothTrackingEnabled) {
            trackedBoxes.clear()
            trackedBoxes.addAll(newDetections)
            return newDetections
        }

        val updatedTracked = mutableListOf<DetectionBox>()
        val unmatchedNew = newDetections.toMutableList()

        // 1. Match current tracked boxes with new detections
        for (tracked in trackedBoxes) {
            var bestMatchIdx = -1
            var bestIoU = 0.20f // Lower threshold for fast motion

            for (i in unmatchedNew.indices) {
                val candidate = unmatchedNew[i]
                if (candidate.category == tracked.category) {
                    val iou = computeIoU(tracked.rect, candidate.rect)
                    if (iou > bestIoU) {
                        bestIoU = iou
                        bestMatchIdx = i
                    }
                }
            }

            if (bestMatchIdx >= 0) {
                // Matched! Smooth position using linear interpolation (Lerp)
                val match = unmatchedNew.removeAt(bestMatchIdx)
                val smoothedRect = RectF(
                    lerp(tracked.rect.left, match.rect.left, smoothingFactor),
                    lerp(tracked.rect.top, match.rect.top, smoothingFactor),
                    lerp(tracked.rect.right, match.rect.right, smoothingFactor),
                    lerp(tracked.rect.bottom, match.rect.bottom, smoothingFactor)
                )

                tracked.rect = smoothedRect
                tracked.framesSinceDetected = 0
                tracked.phrase = match.phrase
                updatedTracked.add(tracked)
            } else {
                // Not detected in this frame, apply persistence
                tracked.framesSinceDetected++
                if (tracked.framesSinceDetected <= maxPersistenceFrames) {
                    updatedTracked.add(tracked)
                }
            }
        }

        // 2. Add brand new detections that didn't match existing tracks
        for (newBox in unmatchedNew) {
            newBox.framesSinceDetected = 0
            updatedTracked.add(newBox)
        }

        trackedBoxes.clear()
        trackedBoxes.addAll(updatedTracked)
        return trackedBoxes.toList()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    private fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        if (interArea <= 0f) return 0f

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    @Synchronized
    fun clear() {
        trackedBoxes.clear()
    }
}
