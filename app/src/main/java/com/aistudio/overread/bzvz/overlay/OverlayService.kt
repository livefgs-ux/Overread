package com.aistudio.overread.bzvz.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aistudio.overread.bzvz.MainActivity
import com.aistudio.overread.bzvz.data.UserPreferencesRepository
import com.aistudio.overread.bzvz.render.RenderTextBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OverlayService - Manages the floating button and translation overlays.
 *
 * This service runs continuously while OverRead is active and:
 * 1. Shows a draggable floating button for manual translation
 * 2. Receives broadcasts from LiveReadingService to show/clear translations automatically
 * 3. Renders webtoon-style translation bubbles over the original text
 */
class OverlayService : Service() {

    companion object {
        const val TAG = "OverlayService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "overlay_service_channel"

        // Broadcast actions from LiveReadingService
        const val ACTION_TRANSLATION_UPDATE = "com.aistudio.overread.TRANSLATION_UPDATE"

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

    private var floatingButtonController: FloatingButtonController? = null
    private var quickMenuController: QuickMenuController? = null
    private var translationOverlayController: com.aistudio.overread.bzvz.render.TranslationOverlayController? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: UserPreferencesRepository
    private var overlayOpacity: Float = 0.92f

    // Broadcast receiver for Live Reading translation updates
    private val translationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("action")) {
                "show" -> handleShowTranslations(intent)
                "clear" -> handleClearTranslations()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = UserPreferencesRepository(this)
        createNotificationChannel()
        startForegroundService()

        // Initialize controllers
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

        // Register broadcast receiver for live translation updates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                translationReceiver,
                IntentFilter(ACTION_TRANSLATION_UPDATE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(translationReceiver, IntentFilter(ACTION_TRANSLATION_UPDATE))
        }

        // Collect preferences
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

        // Existing capture state observer
        scope.launch {
            com.aistudio.overread.bzvz.capture.ScreenCaptureManager.captureState.collect { state ->
                if (state == com.aistudio.overread.bzvz.capture.CaptureState.Capturing) {
                    com.aistudio.overread.bzvz.render.RenderManager.reset()
                }
            }
        }

        // Translation state observer (for manual capture mode)
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
                            // Clear overlays when no text found
                            translationOverlayController?.clear()
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

        // Render state observer
        scope.launch {
            com.aistudio.overread.bzvz.render.RenderManager.renderState.collect { state ->
                if (state == com.aistudio.overread.bzvz.render.RenderState.Success) {
                    val result = com.aistudio.overread.bzvz.render.RenderManager.lastRenderResult.value
                    if (result != null && result.success) {
                        translationOverlayController?.showTranslations(result.boxes, overlayOpacity)
                    }
                } else if (state == com.aistudio.overread.bzvz.render.RenderState.Idle) {
                    translationOverlayController?.clear()
                }
            }
        }
    }

    /**
     * Handle show translations broadcast from LiveReadingService
     */
    private fun handleShowTranslations(intent: Intent) {
        try {
            val boxCount = intent.getIntExtra("box_count", 0)
            if (boxCount == 0) return

            val boxes = mutableListOf<RenderTextBox>()
            for (i in 0 until boxCount) {
                val text = intent.getStringExtra("box_${i}_text") ?: continue
                val original = intent.getStringExtra("box_${i}_original") ?: ""
                val left = intent.getFloatExtra("box_${i}_left", 0f)
                val top = intent.getFloatExtra("box_${i}_top", 0f)
                val right = intent.getFloatExtra("box_${i}_right", 0f)
                val bottom = intent.getFloatExtra("box_${i}_bottom", 0f)

                if (text.isNotBlank()) {
                    boxes.add(RenderTextBox(
                        id = "live_$i",
                        translatedText = text,
                        originalText = original,
                        sourceBoundingBox = null,
                        screenBoundingBox = RectF(left, top, right, bottom),
                        sourceLanguage = null,
                        targetLanguage = ""
                    ))
                }
            }

            if (boxes.isNotEmpty()) {
                translationOverlayController?.showTranslations(boxes, overlayOpacity)
                Log.d(TAG, "Live mode: Showing ${boxes.size} translation bubbles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling show translations: ${e.message}")
        }
    }

    /**
     * Handle clear translations broadcast from LiveReadingService
     */
    private fun handleClearTranslations() {
        translationOverlayController?.clear()
        Log.d(TAG, "Live mode: Cleared translation bubbles")
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
            .setContentText("Tap the floating button to translate text. Live mode auto-translates when scrolling stops.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
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
        try {
            unregisterReceiver(translationReceiver)
        } catch (_: Exception) {
            // Receiver may not be registered
        }
        quickMenuController?.hide()
        floatingButtonController?.hide()
        translationOverlayController?.clear()
        scope.cancel()
        com.aistudio.overread.bzvz.vision.VisionManager.updateState(com.aistudio.overread.bzvz.vision.OcrState.Idle)
        com.aistudio.overread.bzvz.translation.TranslationManager.reset()
        com.aistudio.overread.bzvz.render.RenderManager.reset()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
