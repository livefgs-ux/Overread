package com.example.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class FloatingButtonController(
    private val context: Context,
    private val onShortClick: () -> Unit,
    private val onLongClick: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentSizeName: String = "Medium"

    fun updateSize(sizeName: String) {
        currentSizeName = sizeName
        if (floatingView == null) {
            show()
        } else {
            // Update existing view size
            val scale = when(sizeName) {
                "Small" -> 44
                "Large" -> 76
                else -> 60
            }
            val sizePx = (scale * context.resources.displayMetrics.density).toInt()
            
            floatingView?.getChildAt(0)?.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            windowManager.updateViewLayout(floatingView, layoutParams)
        }
    }

    fun show() {
        if (floatingView != null) return

        val scale = when(currentSizeName) {
            "Small" -> 44
            "Large" -> 76
            else -> 60
        }

        val sizePx = (scale * context.resources.displayMetrics.density).toInt()
        val paddingPx = (12 * context.resources.displayMetrics.density).toInt()

        floatingView = FrameLayout(context).apply {
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame) // Simple shadow bg
            
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_edit) // Placeholder icon
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            addView(icon, FrameLayout.LayoutParams(sizePx, sizePx))
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var touchDownTime = 0L

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    touchDownTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams?.x = initialX + dx.toInt()
                        layoutParams?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - touchDownTime
                    if (!isDragging) {
                        if (clickDuration < ViewConfiguration.getLongPressTimeout()) {
                            onShortClick()
                        } else {
                            onLongClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, layoutParams)
    }

    fun hide() {
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
    }
}

