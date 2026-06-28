package com.aistudio.overread.bzvz.render

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * TranslationOverlayController - Renders webtoon-style speech bubble translations.
 *
 * Features:
 * - Comic book style rounded bubbles with semi-transparent background
 * - Auto-sizing font based on text length and bubble dimensions
 * - Smart text fitting to prevent overflow
 * - Smooth show/hide animations
 * - Minimally obtrusive design that covers original text naturally
 */
class TranslationOverlayController(
    private val context: Context
) {
    companion object {
        private const val TAG = "TranslationOverlay"

        // Bubble styling constants
        private const val BUBBLE_CORNER_RADIUS_DP = 16f
        private const val BUBBLE_PADDING_HORIZONTAL_DP = 20f
        private const val BUBBLE_PADDING_VERTICAL_DP = 14f
        private const val BUBBLE_ELEVATION_DP = 4f
        private const val BUBBLE_STROKE_WIDTH_DP = 1.5f
        private const val MIN_FONT_SIZE_SP = 11f
        private const val MAX_FONT_SIZE_SP = 20f
        private const val DEFAULT_FONT_SIZE_SP = 15f
        private const val TEXT_LINE_SPACING_MULTIPLIER = 1.15f
        private const val MAX_LINES = 12
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: FrameLayout? = null

    // Webtoon-style bubble colors (customizable)
    private var bubbleBackgroundColor = Color.parseColor("#F2FFFFFF") // 95% white
    private var bubbleStrokeColor = Color.parseColor("#E6000000")    // Near-black border
    private var bubbleTextColor = Color.parseColor("#DE000000")      // High-contrast dark text

    /**
     * Show translation bubbles on screen with webtoon-style rendering
     */
    fun showTranslations(boxes: List<RenderTextBox>, opacity: Float) {
        clear()

        if (boxes.isEmpty()) {
            Log.d(TAG, "No boxes to render")
            return
        }

        overlayRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Calculate alpha from opacity setting
        val alphaInt = (opacity * 255).toInt().coerceIn(40, 255)

        // Adjust background alpha based on opacity setting
        val bgColor = if (opacity < 0.5f) {
            // More transparent mode - use darker background for readability
            Color.argb(alphaInt, 255, 255, 255)
        } else {
            // Solid mode - clean white bubble
            Color.argb(alphaInt, 255, 255, 255)
        }

        for (box in boxes) {
            val bubbleView = createBubbleView(box, bgColor, alphaInt)
            val layoutParams = createBubbleLayoutParams(box)
            overlayRoot?.addView(bubbleView, layoutParams)
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(overlayRoot, windowParams)
            Log.d(TAG, "Rendered ${boxes.size} translation bubbles")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay: ${e.message}")
        }
    }

    /**
     * Clear all translation overlays from screen
     */
    fun clear() {
        overlayRoot?.let {
            if (it.isAttachedToWindow || it.windowToken != null) {
                try {
                    windowManager.removeViewImmediate(it)
                } catch (e: Exception) {
                    // View may not be attached, ignore
                }
            }
            overlayRoot = null
        }
    }

    /**
     * Create a single webtoon-style bubble TextView
     */
    private fun createBubbleView(box: RenderTextBox, bgColor: Int, alphaInt: Int): TextView {
        return TextView(context).apply {
            text = box.translatedText
            setTextColor(bubbleTextColor)

            // Calculate optimal font size based on text and available space
            val optimalSize = calculateOptimalFontSize(
                text = box.translatedText,
                availableWidth = box.screenBoundingBox.width(),
                availableHeight = box.screenBoundingBox.height()
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, optimalSize)

            // Line spacing for readability
            setLineSpacing(0f, TEXT_LINE_SPACING_MULTIPLIER)

            // Padding inside bubble
            val paddingH = dpToPx(BUBBLE_PADDING_HORIZONTAL_DP)
            val paddingV = dpToPx(BUBBLE_PADDING_VERTICAL_DP)
            setPadding(paddingH, paddingV, paddingH, paddingV)

            // Webtoon-style bubble background
            background = createBubbleDrawable(bgColor, alphaInt)

            // Text properties
            maxLines = MAX_LINES
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER

            // Enable text shadow for better readability
            setShadowLayer(0.5f, 0f, 0.5f, Color.parseColor("#40FFFFFF"))
        }
    }

    /**
     * Create layout params for a bubble positioned at the speech bubble location
     */
    private fun createBubbleLayoutParams(box: RenderTextBox): FrameLayout.LayoutParams {
        val bubbleWidth = box.screenBoundingBox.width().toInt()
        val bubbleHeight = box.screenBoundingBox.height().toInt()

        // Add padding to make bubble slightly larger than original text area
        val extraPadding = dpToPx(8f)

        return FrameLayout.LayoutParams(
            (bubbleWidth + extraPadding * 2).coerceAtLeast(dpToPx(80f)),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = (box.screenBoundingBox.left - extraPadding).toInt()
            topMargin = (box.screenBoundingBox.top - extraPadding / 2).toInt()

            // Ensure bubble doesn't go off-screen
            if (leftMargin < 0) leftMargin = dpToPx(8f)
            if (topMargin < 0) topMargin = dpToPx(8f)
        }
    }

    /**
     * Create the webtoon-style bubble drawable (rounded rect with optional border)
     */
    private fun createBubbleDrawable(bgColor: Int, alphaInt: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(BUBBLE_CORNER_RADIUS_DP).toFloat()
            setColor(bgColor)

            // Subtle border for bubble definition
            val strokeAlpha = (alphaInt * 0.6f).toInt().coerceIn(30, 180)
            setStroke(
                dpToPx(BUBBLE_STROKE_WIDTH_DP),
                Color.argb(strokeAlpha, 60, 60, 60)
            )
        }
    }

    /**
     * Calculate optimal font size to fit text within the bubble dimensions
     */
    private fun calculateOptimalFontSize(
        text: String,
        availableWidth: Float,
        availableHeight: Float
    ): Float {
        val density = context.resources.displayMetrics.density
        val textLength = text.length
        val hasLongWords = text.split(" ").any { it.length > 12 }

        // Base size calculation
        val baseSize = when {
            textLength <= 15 -> MAX_FONT_SIZE_SP
            textLength <= 40 -> 17f
            textLength <= 80 -> 14f
            textLength <= 150 -> 12f
            else -> MIN_FONT_SIZE_SP
        }

        // Adjust based on available width
        val widthAdjustedSize = if (availableWidth > 0) {
            val widthInDp = availableWidth / density
            when {
                widthInDp < 100 -> MIN_FONT_SIZE_SP
                widthInDp < 200 -> baseSize * 0.85f
                widthInDp > 400 -> (baseSize * 1.1f).coerceAtMost(MAX_FONT_SIZE_SP)
                else -> baseSize
            }
        } else {
            baseSize
        }

        // Adjust for long words (Korean/Chinese/Japanese text often has no spaces)
        return if (hasLongWords) {
            (widthAdjustedSize * 0.9f).coerceAtLeast(MIN_FONT_SIZE_SP)
        } else {
            widthAdjustedSize.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }
    }

    /**
     * Show a single bubble from parsed broadcast data (used by OverlayService from Live mode)
     */
    fun showSingleBubble(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        opacity: Float = 0.92f
    ) {
        val box = RenderTextBox(
            id = java.util.UUID.randomUUID().toString(),
            translatedText = text,
            originalText = "",
            sourceBoundingBox = null,
            screenBoundingBox = android.graphics.RectF(left, top, right, bottom),
            sourceLanguage = null,
            targetLanguage = ""
        )
        showTranslations(listOf(box), opacity)
    }

    /**
     * Check if overlay is currently visible
     */
    fun isVisible(): Boolean {
        return overlayRoot != null && overlayRoot!!.isAttachedToWindow
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
