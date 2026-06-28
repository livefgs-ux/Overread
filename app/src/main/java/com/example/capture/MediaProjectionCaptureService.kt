package com.example.capture

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
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MediaProjectionCaptureService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaProjection: MediaProjection? = null
    private var windowManager: WindowManager? = null

    companion object {
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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") {
            startForegroundService()
            setupMediaProjection()
            observeCaptureRequests()
        } else if (intent?.action == "STOP") {
            stopSelf()
        }
        return START_NOT_STICKY
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
                CHANNEL_ID,
                "Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun setupMediaProjection() {
        if (mediaProjection != null) return // Already setup

        val resultCode = ScreenCaptureManager.permissionResultCode
        val data = ScreenCaptureManager.permissionResultData

        if (resultCode != 0 && data != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                mediaProjection = mpm.getMediaProjection(resultCode, data)
                ScreenCaptureManager.captureState.value = CaptureState.Prepared
            } catch (e: Exception) {
                ScreenCaptureManager.captureState.value = CaptureState.Failed
                ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                    success = false,
                    errorMessage = e.message
                )
                stopSelf()
            }
        }
    }

    private fun observeCaptureRequests() {
        scope.launch {
            ScreenCaptureManager.captureRequests.collect {
                if (mediaProjection != null) {
                    captureSingleFrame()
                } else {
                    Toast.makeText(this@MediaProjectionCaptureService, "MediaProjection not ready", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun captureSingleFrame() {
        ScreenCaptureManager.captureState.value = CaptureState.Capturing
        
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "OverReadCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )
        } catch (e: Exception) {
            ScreenCaptureManager.captureState.value = CaptureState.Failed
            ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                success = false,
                errorMessage = e.message
            )
            stopSelf()
            return
        }

        imageReader.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }

            if (image != null) {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.Default) {
                            val planes = image.planes
                            if (planes.isNotEmpty()) {
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * width
                                
                                val bitmapWidth = width + rowPadding / pixelStride
                                if (bitmapWidth > 0 && height > 0) {
                                    val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                                    bitmap.copyPixelsFromBuffer(buffer)
                                    
                                    val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                                    
                                    com.example.vision.VisionManager.updateState(com.example.vision.OcrState.Processing)
                                    
                                    // Run OCR
                                    val ocrResult = com.example.vision.OcrEngine.processBitmap(finalBitmap)
                                    
                                    // Clean up bitmaps immediately after OCR and before text processing
                                    finalBitmap.recycle()
                                    bitmap.recycle()

                                    val repository = com.example.data.UserPreferencesRepository(this@MediaProjectionCaptureService)
                                    val targetLanguage = repository.targetLanguageFlow.first() 
                                    com.example.vision.VisionManager.processOcrResult(ocrResult, targetLanguage)
                                    
                                    ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                                        success = true,
                                        width = width,
                                        height = height
                                    )
                                    ScreenCaptureManager.captureState.value = CaptureState.Success
                                }
                            }
                        }
                    } catch (e: Exception) {
                        ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                            success = false,
                            errorMessage = "Failed to process screen capture."
                        )
                        ScreenCaptureManager.captureState.value = CaptureState.Failed
                        com.example.vision.VisionManager.updateState(com.example.vision.OcrState.Failed)
                    } finally {
                        image.close()
                        // Cleanup resources immediately after capturing ONE frame
                        virtualDisplay?.release()
                        reader.setOnImageAvailableListener(null, null)
                        reader.close()
                        stopSelf()
                    }
                }
            } else {
                ScreenCaptureManager.lastCaptureResult.value = CaptureResult(
                    success = false,
                    errorMessage = "Screen capture was blocked by this app or the system.",
                    isSecureOrEmpty = true
                )
                ScreenCaptureManager.captureState.value = CaptureState.Failed
                com.example.vision.VisionManager.updateState(com.example.vision.OcrState.Failed)
                
                virtualDisplay?.release()
                reader.setOnImageAvailableListener(null, null)
                reader.close()
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mediaProjection?.stop()
        mediaProjection = null
        ScreenCaptureManager.clear()
        ScreenCaptureManager.captureState.value = CaptureState.Idle
    }

    override fun onBind(intent: Intent): IBinder? = null
}
