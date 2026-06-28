package com.example.render

import android.graphics.Rect
import android.graphics.RectF

object OverlayBoxCalculator {
    fun calculateScreenBounds(
        sourceBoundingBox: Rect?,
        captureWidth: Int,
        captureHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): RectF {
        if (sourceBoundingBox == null || captureWidth <= 0 || captureHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return RectF(0f, 0f, 0f, 0f)
        }

        val scaleX = screenWidth.toFloat() / captureWidth.toFloat()
        val scaleY = screenHeight.toFloat() / captureHeight.toFloat()

        var left = sourceBoundingBox.left * scaleX
        var top = sourceBoundingBox.top * scaleY
        var right = sourceBoundingBox.right * scaleX
        var bottom = sourceBoundingBox.bottom * scaleY

        // Ensure minimum width and height to make it visible and usable
        val minWidth = 100f // roughly 100px min width
        val minHeight = 48f // roughly 24dp min height

        if (right - left < minWidth) {
            val center = left + (right - left) / 2
            left = center - minWidth / 2
            right = center + minWidth / 2
        }

        if (bottom - top < minHeight) {
            val center = top + (bottom - top) / 2
            top = center - minHeight / 2
            bottom = center + minHeight / 2
        }

        // Ensure maximum width so it doesn't span entire horizontal space
        val maxWidth = screenWidth * 0.8f
        if (right - left > maxWidth) {
            val center = left + (right - left) / 2
            left = center - maxWidth / 2
            right = center + maxWidth / 2
        }

        // Apply margins from screen edges
        val edgeMargin = 16f

        if (left < edgeMargin) {
            right += (edgeMargin - left)
            left = edgeMargin
        }
        if (top < edgeMargin) {
            bottom += (edgeMargin - top)
            top = edgeMargin
        }
        if (right > screenWidth - edgeMargin) {
            left -= (right - (screenWidth - edgeMargin))
            right = screenWidth - edgeMargin
        }
        if (bottom > screenHeight - edgeMargin) {
            top -= (bottom - (screenHeight - edgeMargin))
            bottom = screenHeight - edgeMargin
        }
        
        // Final clamp just in case after adjustments
        return RectF(
            left.coerceAtLeast(edgeMargin),
            top.coerceAtLeast(edgeMargin),
            right.coerceAtMost(screenWidth - edgeMargin),
            bottom.coerceAtMost(screenHeight - edgeMargin)
        )
    }
}
