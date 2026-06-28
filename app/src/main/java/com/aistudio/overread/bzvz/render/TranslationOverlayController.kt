package com.aistudio.overread.bzvz.render

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class TranslationOverlayController(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: FrameLayout? = null
    
    fun showTranslations(boxes: List<RenderTextBox>, opacity: Float) {
        clear()
        
        if (boxes.isEmpty()) return

        overlayRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
        val backgroundColor = Color.argb(alphaInt, 20, 20, 20)

        for (box in boxes) {
            val textView = TextView(context).apply {
                text = box.translatedText
                setTextColor(Color.WHITE)
                
                // Basic auto font-size based on text length
                val textLength = box.translatedText.length
                textSize = when {
                    textLength < 20 -> 16f
                    textLength < 60 -> 14f
                    else -> 12f
                }
                
                setPadding(24, 16, 24, 16)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(backgroundColor)
                    cornerRadius = 24f // ~12dp rough translation
                }
                
                // Allow multiple lines but prevent overflowing screen too much
                maxLines = 10
                ellipsize = TextUtils.TruncateAt.END
            }

            val exactWidth = box.screenBoundingBox.width().toInt()
            val minHeight = box.screenBoundingBox.height().toInt()
            
            textView.minimumHeight = minHeight

            val layoutParams = FrameLayout.LayoutParams(
                exactWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = box.screenBoundingBox.left.toInt()
                topMargin = box.screenBoundingBox.top.toInt()
            }

            overlayRoot?.addView(textView, layoutParams)
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayRoot, windowParams)
    }

    fun clear() {
        overlayRoot?.let {
            if (it.isAttachedToWindow || it.windowToken != null) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    // Ignore view not attached
                }
            }
            overlayRoot = null
        }
    }
}
