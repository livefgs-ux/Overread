package com.example.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity

class QuickMenuController(
    private val context: Context,
    private val onStopService: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var menuView: LinearLayout? = null

    fun show() {
        if (menuView != null) return

        menuView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000")) // Semi-transparent black
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER
            
            val title = TextView(context).apply {
                text = "OverRead Quick Menu"
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(0, 0, 0, 32)
                gravity = Gravity.CENTER
            }
            addView(title)

            // Button 1: Capture
            addView(createButton("Capture & translate text") {
                val state = com.example.capture.ScreenCaptureManager.captureState.value
                if (state == com.example.capture.CaptureState.Prepared || state == com.example.capture.CaptureState.Success || state == com.example.capture.CaptureState.Failed) {
                    com.example.capture.ScreenCaptureManager.requestCapture()
                    android.widget.Toast.makeText(context, "Processing requested...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Please prepare capture in the app first", android.widget.Toast.LENGTH_SHORT).show()
                }
                hide()
            })

            // Button 2: Clear Overlay (Placeholder)
            addView(createButton("Clear translation boxes") {
                com.example.render.RenderManager.reset()
                hide()
            })
            
            // Button 3: Settings
            addView(createButton("Open Settings") {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                hide()
            })

            // Button 4: Stop Service
            addView(createButton("Stop OverRead") {
                onStopService()
                hide()
            })
            
            // Button 5: Close menu
            addView(createButton("Close Menu") {
                hide()
            })
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(menuView, layoutParams)
    }

    private fun createButton(textStr: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = textStr
            setOnClickListener { onClick() }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            layoutParams = params
        }
    }

    fun hide() {
        if (menuView != null) {
            windowManager.removeView(menuView)
            menuView = null
        }
    }
}
