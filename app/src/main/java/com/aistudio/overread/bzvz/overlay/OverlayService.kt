package com.aistudio.overread.bzvz.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aistudio.overread.bzvz.MainActivity
import com.aistudio.overread.bzvz.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var floatingButtonController: FloatingButtonController? = null
    private var quickMenuController: QuickMenuController? = null
    private var translationOverlayController: com.aistudio.overread.bzvz.render.TranslationOverlayController? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: UserPreferencesRepository
    private var overlayOpacity: Float = 0.8f

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "overlay_service_channel"
        
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = UserPreferencesRepository(this)
        createNotificationChannel()
        startForegroundService()

        quickMenuController = QuickMenuController(this, onStopService = {
            stopSelf()
        })
        
        floatingButtonController = FloatingButtonController(
            context = this,
            onShortClick = {
                val state = com.aistudio.overread.bzvz.capture.ScreenCaptureManager.captureState.value
                val isPrepared = com.aistudio.overread.bzvz.capture.ScreenCaptureManager.isPrepared
                if (state == com.aistudio.overread.bzvz.capture.CaptureState.Capturing) {
                    val ocrState = com.aistudio.overread.bzvz.vision.VisionManager.ocrState.value
                    if (ocrState == com.aistudio.overread.bzvz.vision.OcrState.Processing || ocrState == com.aistudio.overread.bzvz.vision.OcrState.TextProcessing) {
                        android.widget.Toast.makeText(this, "Processing already running...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this, "Capture in progress...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else if (isPrepared) {
                    com.aistudio.overread.bzvz.capture.MediaProjectionCaptureService.start(this)
                    android.widget.Toast.makeText(this, "Processing screen...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "Prepare screen capture in OverRead first.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onLongClick = {
                quickMenuController?.show()
            }
        )
        
        translationOverlayController = com.aistudio.overread.bzvz.render.TranslationOverlayController(this)

        scope.launch {
            repository.floatingButtonSizeFlow.collect { sizeName ->
                floatingButtonController?.updateSize(sizeName)
            }
        }

        scope.launch {
            repository.overlayOpacityFlow.collect { opacity ->
                overlayOpacity = opacity
            }
        }
        
        scope.launch {
            com.aistudio.overread.bzvz.capture.ScreenCaptureManager.captureState.collect { state ->
                if (state == com.aistudio.overread.bzvz.capture.CaptureState.Capturing) {
                    com.aistudio.overread.bzvz.render.RenderManager.reset()
                }
            }
        }
        
        scope.launch {
            com.aistudio.overread.bzvz.translation.TranslationManager.translationState.collect { state ->
                when (state) {
                    com.aistudio.overread.bzvz.translation.TranslationState.Success -> {
                        val result = com.aistudio.overread.bzvz.translation.TranslationManager.lastTranslationResult.value
                        val captureResult = com.aistudio.overread.bzvz.capture.ScreenCaptureManager.lastCaptureResult.value
                        if (result != null) {
                            val metrics = android.util.DisplayMetrics()
                            val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                            @Suppress("DEPRECATION")
                            windowManager.defaultDisplay.getRealMetrics(metrics)

                            com.aistudio.overread.bzvz.render.RenderManager.processTranslation(
                                translationResult = result,
                                captureWidth = captureResult?.width,
                                captureHeight = captureResult?.height,
                                screenWidth = metrics.widthPixels,
                                screenHeight = metrics.heightPixels
                            )
                        }
                    }
                    com.aistudio.overread.bzvz.translation.TranslationState.ModelRequired -> {
                        android.widget.Toast.makeText(this@OverlayService, "Translation model required. Open OverRead to prepare it.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    com.aistudio.overread.bzvz.translation.TranslationState.Failed -> {
                        android.widget.Toast.makeText(this@OverlayService, "Translation failed. Try again or prepare the model.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    com.aistudio.overread.bzvz.translation.TranslationState.Skipped -> {
                        val reason = com.aistudio.overread.bzvz.translation.TranslationManager.lastTranslationResult.value?.skippedReason
                        if (reason == com.aistudio.overread.bzvz.translation.TranslationSkippedReason.NO_TEXT) {
                            android.widget.Toast.makeText(this@OverlayService, "No text found on this screen.", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (reason == com.aistudio.overread.bzvz.translation.TranslationSkippedReason.SAME_AS_TARGET) {
                            android.widget.Toast.makeText(this@OverlayService, "Detected language already matches target language.", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (reason != com.aistudio.overread.bzvz.translation.TranslationSkippedReason.MODEL_NOT_READY) {
                            android.widget.Toast.makeText(this@OverlayService, "Translation skipped.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            }
        }
        
        scope.launch {
            com.aistudio.overread.bzvz.render.RenderManager.renderState.collect { state ->
                if (state == com.aistudio.overread.bzvz.render.RenderState.Success) {
                    val result = com.aistudio.overread.bzvz.render.RenderManager.lastRenderResult.value
                    if (result != null && result.success) {
                        translationOverlayController?.showTranslations(result.boxes, overlayOpacity)
                        android.widget.Toast.makeText(this@OverlayService, "Translation boxes rendered", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else if (state == com.aistudio.overread.bzvz.render.RenderState.Idle) {
                    translationOverlayController?.clear()
                }
            }
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OverRead Overlay is Active")
            .setContentText("Tap the floating button to translate text")
            .setSmallIcon(android.R.drawable.ic_menu_edit) // Placeholder icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "OverRead Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        quickMenuController?.hide()
        floatingButtonController?.hide()
        translationOverlayController?.clear()
        scope.cancel()
        com.aistudio.overread.bzvz.vision.VisionManager.updateState(com.aistudio.overread.bzvz.vision.OcrState.Idle)
        com.aistudio.overread.bzvz.translation.TranslationManager.reset()
        com.aistudio.overread.bzvz.render.RenderManager.reset()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}

