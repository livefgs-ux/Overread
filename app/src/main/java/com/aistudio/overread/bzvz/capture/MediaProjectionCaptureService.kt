package com.aistudio.overread.bzvz.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aistudio.overread.bzvz.MainActivity
import com.aistudio.overread.bzvz.vision.OcrEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * MediaProjectionCaptureService - Single-shot screen capture service.
 *
 * Used for manual capture mode (tapping the floating button).
 * For real-time continuous translation, see LiveReadingService.
 */
class MediaProjectionCaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "capture_service_channel"

        fun start(context: Context) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = "STOP"
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaProjection: MediaProjection? = null
    private var windowManager: WindowManager? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var isCleaningUp = false

    private fun cleanupCaptureResources() {
        if (isCleaningUp) return
        isCleaningUp = true
        ScreenCaptureManager.captureStage.value = CaptureStage.CleanupStarted
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
        } catch (_: Exception) {}

        try {
            mediaProjectionCallback?.let { mediaProjection?.unregisterCallback(it) }
            mediaProjectionCallback = null
        } catch (_: Exception) {}

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}

        ScreenCaptureManager.captureStage.value = CaptureStage.CleanupCompleted
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startForegroundService()
            setupMediaProjection()
            if (mediaProjection != null) {
                captureSingleFrame()
            }
        } else if (intent?.action == "STOP") {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OverRead Capture Prepared")
            .setContentText("Ready to capture screen when requested")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Capture Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun setupMediaProjection() {
        if (mediaProjection != null) return

        val (resultCode, data) = ScreenCaptureManager.getPermissionData()

        if (resultCode != 0 && data != null) {
            ScreenCaptureManager.captureStage.value = CaptureStage.CreatingMediaProjection
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                mediaProjection = mpm.getMediaProjection(resultCode, data)

                ScreenCaptureManager.captureStage.value = CaptureStage.RegisteringCallback
                mediaProjectionCallback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        cleanupCaptureResources()
                    }
                }
                mediaProjection?.registerCallback(mediaProjectionCallback!!, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                handleSetupError()
            }
        }
    }

    private fun handleSetupError() {
        ScreenCaptureManager.captureState.value = CaptureState.Failed
        ScreenCaptureManager.captureStage.value = CaptureStage.CaptureFailed
        ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
            success = false, errorMessage = "Screen capture setup failed. Please prepare capture again."
        )
        stopSelf()
    }

    private var captureJob: Job? = null

    private fun captureSingleFrame() {
        ScreenCaptureManager.captureState.value = CaptureState.Capturing
        ScreenCaptureManager.captureStage.value = CaptureStage.CreatingImageReader

        // Reset VisionManager state at the beginning of a capture
        com.aistudio.overread.bzvz.vision.VisionManager.updateState(com.aistudio.overread.bzvz.vision.OcrState.Idle)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        try {
            ScreenCaptureManager.captureStage.value = CaptureStage.CreatingVirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "OverReadCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            handleCaptureError("Screen capture setup failed. Please prepare capture again.")
            return
        }

        ScreenCaptureManager.captureStage.value = CaptureStage.WaitingForFrame

        // Pipeline timeout
        captureJob = scope.launch {
            delay(12000)
            if (ScreenCaptureManager.captureState.value != CaptureState.Success &&
                ScreenCaptureManager.captureState.value != CaptureState.Failed &&
                ScreenCaptureManager.captureState.value != CaptureState.Idle
            ) {
                handleCaptureError("Text detection timed out. Please try again.")
            }
        }

        val frameTimeoutJob = scope.launch {
            delay(3000)
            if (ScreenCaptureManager.captureStage.value == CaptureStage.WaitingForFrame) {
                handleCaptureError("No frame was received from the screen. Try again.")
            }
        }

        imageReader!!.setOnImageAvailableListener({ reader ->
            imageReader?.setOnImageAvailableListener(null, null)
            frameTimeoutJob.cancel()
            ScreenCaptureManager.captureStage.value = CaptureStage.ImageAvailable
            val image = try {
                reader.acquireLatestImage()
            } catch (_: Exception) {
                null
            }

            if (image != null) {
                ScreenCaptureManager.captureStage.value = CaptureStage.AcquiringImage
                scope.launch {
                    processImage(image, width, height)
                }
            } else {
                handleCaptureError("Screen capture was blocked by this app or the system.", isSecure = true)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private suspend fun processImage(image: android.media.Image, width: Int, height: Int) {
        try {
            withTimeout(8000) {
                withContext(Dispatchers.Default) {
                    ScreenCaptureManager.captureStage.value = CaptureStage.ConvertingBitmap
                    val planes = image.planes
                    if (planes.isEmpty()) throw Exception("Image has no planes")

                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmapWidth = width + rowPadding / pixelStride
                    if (bitmapWidth <= 0 || height <= 0) throw Exception("Invalid bitmap dimensions")

                    val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    ScreenCaptureManager.captureStage.value = CaptureStage.BitmapReady

                    withContext(Dispatchers.Main) {
                        ScreenCaptureManager.captureStage.value = CaptureStage.SendingToOcr
                        com.aistudio.overread.bzvz.vision.VisionManager.updateState(
                            com.aistudio.overread.bzvz.vision.OcrState.Processing
                        )
                    }

                    // Run OCR with multi-script support for webtoon translation
                    ScreenCaptureManager.captureStage.value = CaptureStage.OcrProcessing
                    Log.d(TAG, "Running multi-script OCR on captured frame")
                    val ocrResult = OcrEngine.processBitmapMultiScript(finalBitmap)

                    // Clean up bitmaps immediately after OCR
                    finalBitmap.recycle()
                    bitmap.recycle()

                    val repository = com.aistudio.overread.bzvz.data.UserPreferencesRepository(
                        this@MediaProjectionCaptureService
                    )
                    val targetLanguage = repository.targetLanguageFlow.first()
                    withContext(Dispatchers.Main) {
                        com.aistudio.overread.bzvz.vision.VisionManager.processOcrResult(ocrResult, targetLanguage)
                        ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                            success = true, width = width, height = height
                        )
                        ScreenCaptureManager.captureState.value = CaptureState.Success
                        ScreenCaptureManager.captureStage.value = CaptureStage.CaptureCompleted
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            handleCaptureError("OCR timed out. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}")
            handleCaptureError("Failed to convert captured frame.")
        } finally {
            image.close()
            withContext(Dispatchers.Main) {
                cleanupCaptureResources()
            }
        }
    }

    private fun handleCaptureError(message: String, isSecure: Boolean = false) {
        ScreenCaptureManager.captureState.value = CaptureState.Failed
        ScreenCaptureManager.captureStage.value = CaptureStage.CaptureFailed
        ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
            success = false, errorMessage = message, isSecureOrEmpty = isSecure
        )
        com.aistudio.overread.bzvz.vision.VisionManager.updateState(com.aistudio.overread.bzvz.vision.OcrState.Failed)
        cleanupCaptureResources()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onBind(intent: Intent): IBinder? = null
}
