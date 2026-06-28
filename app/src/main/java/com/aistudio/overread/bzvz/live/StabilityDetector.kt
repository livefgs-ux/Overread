package com.aistudio.overread.bzvz.live

import android.media.Image

class StabilityDetector(
    private val stableDurationMs: Long = 900L,
    private val tolerance: Int = 0 // Exactly the same for now, or allow small variation? A simple hash requires exact match. 
) {
    private var lastFingerprint: Int = 0
    private var firstSeenTimestampMs: Long = 0L

    fun processFrame(image: Image, timestampMs: Long): StabilityState {
        val currentFingerprint = generateFingerprint(image)

        if (currentFingerprint == 0) {
            return StabilityState.Unknown
        }

        if (currentFingerprint != lastFingerprint) {
            lastFingerprint = currentFingerprint
            firstSeenTimestampMs = timestampMs
            return StabilityState.Moving
        }

        // Fingerprint is the same
        val durationStable = timestampMs - firstSeenTimestampMs
        if (durationStable >= stableDurationMs) {
            return StabilityState.Stable
        }

        return StabilityState.Stabilizing
    }

    fun clear() {
        lastFingerprint = 0
        firstSeenTimestampMs = 0L
    }

    private fun generateFingerprint(image: Image): Int {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height

            var hash = 0
            val xStep = width / 10
            val yStep = height / 10

            // Sample an 81-point grid across the screen
            for (my in 1..9) {
                for (mx in 1..9) {
                    val x = mx * xStep
                    val y = my * yStep
                    val index = y * rowStride + x * pixelStride
                    
                    if (index >= 0 && index < buffer.capacity() - 2) {
                        val r = buffer.get(index).toInt() and 0xFF
                        val g = buffer.get(index + 1).toInt() and 0xFF
                        val b = buffer.get(index + 2).toInt() and 0xFF
                        hash = 31 * hash + (r + g + b)
                    }
                }
            }
            return if (hash == 0) 1 else hash // 0 is reserved for 'not generated'
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
}
