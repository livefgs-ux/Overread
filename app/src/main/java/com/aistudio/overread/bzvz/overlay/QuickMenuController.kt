package com.aistudio.overread.bzvz.overlay

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
import com.aistudio.overread.bzvz.MainActivity

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

            // Button 1: Live Reading Start/Stop
            val liveState = com.aistudio.overread.bzvz.live.LiveModeController.state.value
            if (liveState == com.aistudio.overread.bzvz.live.LiveSessionState.Idle || liveState == com.aistudio.overread.bzvz.live.LiveSessionState.Stopped || liveState == com.aistudio.overread.bzvz.live.LiveSessionState.Failed) {
                addView(createButton("Start Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.startIntent()
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    android.widget.Toast.makeText(context, "Please start Live Reading from app", android.widget.Toast.LENGTH_SHORT).show()
                    hide()
                })
            } else if (liveState == com.aistudio.overread.bzvz.live.LiveSessionState.Active) {
                addView(createButton("Pause Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.pause()
                    hide()
                })
                addView(createButton("Stop Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.stop()
                    hide()
                })
            } else if (liveState == com.aistudio.overread.bzvz.live.LiveSessionState.Paused) {
                addView(createButton("Resume Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.resume()
                    hide()
                })
                addView(createButton("Stop Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.stop()
                    hide()
                })
            } else {
                addView(createButton("Stop Live Reading") {
                    com.aistudio.overread.bzvz.live.LiveModeController.stop()
                    hide()
                })
            }

            // Button 2: Manual Capture (Snapshot Mode)
            addView(createButton("Capture & translate once") {
                val state = com.aistudio.overread.bzvz.capture.ScreenCaptureManager.captureState.value
                val isPrepared = com.aistudio.overread.bzvz.capture.ScreenCaptureManager.isPrepared
                if (state == com.aistudio.overread.bzvz.capture.CaptureState.Capturing) {
                    android.widget.Toast.makeText(context, "Capture already running...", android.widget.Toast.LENGTH_SHORT).show()
                } else if (isPrepared) {
                    com.aistudio.overread.bzvz.capture.MediaProjectionCaptureService.start(context)
                    android.widget.Toast.makeText(context, "Processing screen...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Please prepare capture in the app first", android.widget.Toast.LENGTH_SHORT).show()
                }
                hide()
            })

            // Button 2: Clear Overlay (Placeholder)
            addView(createButton("Clear translation boxes") {
                com.aistudio.overread.bzvz.render.RenderManager.reset()
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
