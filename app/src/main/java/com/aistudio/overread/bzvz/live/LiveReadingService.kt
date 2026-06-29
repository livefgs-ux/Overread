package com.aistudio.overread.bzvz.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
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
import com.aistudio.overread.bzvz.data.UserPreferencesRepository
import com.aistudio.overread.bzvz.vision.OcrEngine
import com.aistudio.overread.bzvz.vision.language.LanguageIdEngine
import com.aistudio.overread.bzvz.vision.processing.TextMerger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live Reading Service - Real-time webtoon translation.
 *
 * Continuously captures screen frames, detects when content is stable,
 * runs OCR + translation pipeline, and shows/hides translation overlays automatically.
 *
 * Flow:
 * 1. Captures frames continuously via MediaProjection
 * 2. FrameSampler throttles processing to avoid overwhelming the device
 * 3. StabilityDetector detects when screen stops changing (user stopped scrolling)
 * 4. When STABLE -> runs OCR -> Language ID -> Translation -> Renders overlays
 * 5. When MOVING (user scrolling) -> clears overlays immediately
 * 6. When NO TEXT found on page -> clears overlays (clean page with just images)
 */
class LiveReadingService : Service() {

    companion object {
        private const val TAG = "LiveReadingService"
        private const val CHANNEL_ID = "LiveReadingServiceChannel"
        private const val NOTIFICATION_ID = 200

        // Frame processing config
        private const val FRAME_SAMPLE_INTERVAL_MS = 600L  // Check every 600ms
        private const val STABLE_DURATION_MS = 800L        // Consider stable after 800ms
        private const val OCR_TIMEOUT_MS = 10000L          // 10s timeout for full pipeline
        private const val MIN_TEXT_BLOCKS_TO_RENDER = 1    // Min blocks to show overlay

        const val ACTION_START = "START_LIVE_READING"
        const val ACTION_STOP = "STOP_LIVE_READING"
        const val ACTION_PAUSE = "PAUSE_LIVE_READING"
        const val ACTION_RESUME = "RESUME_LIVE_READING"

        fun start(context: Context) {
            val intent = Intent(context, LiveReadingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveReadingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var isCleaningUp = false
    private val isProcessing = AtomicBoolean(false)
    private val frameSampler = FrameSampler(intervalMs = FRAME_SAMPLE_INTERVAL_MS)
    private val stabilityDetector = StabilityDetector(stableDurationMs = STABLE_DURATION_MS)

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null
    private var lastStableFingerprint: Int = 0

    private lateinit var repository: UserPreferencesRepository
    private var targetLanguage: String = "en"

    override fun onCreate() {
        super.onCreate()
        repository = UserPreferencesRepository(this)
        createNotificationChannel()

        // Load target language
        serviceScope.launch {
            repository.targetLanguageFlow.collect { lang ->
                targetLanguage = lang
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                LiveModeController.stop()
                cleanupAndStop()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                LiveModeController.pause()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                LiveModeController.resume()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundService()
                LiveModeController.setState(LiveSessionState.StartingService)
                startProjectionAndCapture()

                stateObserverJob = serviceScope.launch {
                    LiveModeController.state.collectLatest { state ->
                        if (state == LiveSessionState.Stopping || state == LiveSessionState.Stopped) {
                            cleanupAndStop()
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Reading Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time webtoon translation service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, LiveReadingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, LiveReadingService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resumeIntent = Intent(this, LiveReadingService::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getService(
            this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OverRead Live Translation Active")
            .setContentText("Translating webtoons in real-time. Tap to manage.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjectionAndCapture() {
        val sessionData = MediaProjectionSessionManager.getSessionData()
        if (sessionData == null) {
            LiveModeController.setState(LiveSessionState.Failed)
            cleanupAndStop()
            return
        }

        LiveModeController.setState(LiveSessionState.CreatingProjection)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = try {
            mpm.getMediaProjection(sessionData.first, sessionData.second)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaProjection: ${e.message}")
            LiveModeController.setState(LiveSessionState.Failed)
            cleanupAndStop()
            return
        }

        if (mediaProjection == null) {
            LiveModeController.setState(LiveSessionState.Failed)
            cleanupAndStop()
            return
        }

        LiveModeController.setState(LiveSessionState.RegisteringCallback)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                LiveModeController.stop()
                cleanupAndStop()
            }
        }, Handler(Looper.getMainLooper()))

        LiveModeController.setState(LiveSessionState.CreatingImageReader)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            metrics.widthPixels = windowMetrics.bounds.width()
            metrics.heightPixels = windowMetrics.bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }

            if (image != null) {
                handleFrame(image, width, height)
            }
        }, Handler(Looper.getMainLooper()))

        LiveModeController.setState(LiveSessionState.CreatingVirtualDisplay)

        try {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "LiveReadingVD",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                Handler(Looper.getMainLooper())
            )
            LiveModeController.setState(LiveSessionState.Active)
            Log.i(TAG, "Live reading started - capturing ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay: ${e.message}")
            LiveModeController.setState(LiveSessionState.Failed)
            cleanupAndStop()
        }
    }

    /**
     * Main frame handler - processes each captured frame
     */
    private fun handleFrame(image: Image, width: Int, height: Int) {
        val state = LiveModeController.state.value
        val isPaused = (state == LiveSessionState.Paused)

        if (state != LiveSessionState.Active && !isPaused) {
            image.close()
            return
        }

        LiveModeController.incrementFrames()

        val nowMs = System.currentTimeMillis()

        // Only sample at intervals to avoid overwhelming the device
        if (!frameSampler.shouldSample(nowMs, isPaused)) {
            LiveModeController.incrementFramesDropped()
            image.close()
            return
        }

        LiveModeController.incrementFramesAccepted()

        // Check stability
        val stability = stabilityDetector.processFrame(image, nowMs)
        LiveModeController.setStabilityState(stability)

        when (stability) {
            StabilityState.Moving -> {
                // User is scrolling - clear overlays immediately for clean experience
                if (lastStableFingerprint != 0) {
                    lastStableFingerprint = 0
                    serviceScope.launch(Dispatchers.Main) {
                        sendTranslationBroadcast(clear = true)
                    }
                }
            }
            StabilityState.Stable -> {
                // Screen is stable - process translation if not already processing
                val currentFingerprint = generateQuickFingerprint(image)
                if (currentFingerprint != lastStableFingerprint && isProcessing.compareAndSet(false, true)) {
                    lastStableFingerprint = currentFingerprint
                    // Convert image to bitmap and process
                    val bitmap = imageToBitmap(image, width, height)
                    if (bitmap != null) {
                        serviceScope.launch {
                            processFrameWithPipeline(bitmap, width, height)
                            isProcessing.set(false)
                        }
                    } else {
                        isProcessing.set(false)
                    }
                }
            }
            else -> { /* Stabilizing - do nothing */ }
        }

        image.close()
    }

    /**
     * Full pipeline: OCR -> Language ID -> Translation -> Render
     */
    private suspend fun processFrameWithPipeline(bitmap: Bitmap, captureWidth: Int, captureHeight: Int) {
        try {
            withTimeout(OCR_TIMEOUT_MS) {
                // Step 1: OCR with multi-script support
                val ocrResult = withContext(Dispatchers.Default) {
                    OcrEngine.processBitmapMultiScript(bitmap)
                }

                // Recycle bitmap immediately after OCR
                bitmap.recycle()

                if (!ocrResult.success || ocrResult.blockCount == 0) {
                    Log.d(TAG, "No text found on screen - clearing overlays")
                    withContext(Dispatchers.Main) {
                        sendTranslationBroadcast(clear = true)
                    }
                    return@withTimeout
                }

                // Step 2: Process text blocks
                val processedText = withContext(Dispatchers.Default) {
                    TextMerger.process(ocrResult)
                }

                if (processedText.processedBlockCount == 0) {
                    withContext(Dispatchers.Main) {
                        sendTranslationBroadcast(clear = true)
                    }
                    return@withTimeout
                }

                // Step 3: Language Identification
                val textForLangId = processedText.fullProcessedText
                val langIdResult = withContext(Dispatchers.Default) {
                    LanguageIdEngine.identifyLanguage(textForLangId, targetLanguage)
                }

                // Step 4: Translation (MUST await completion before proceeding)
                withContext(Dispatchers.IO) {
                    com.aistudio.overread.bzvz.translation.TranslationManager.processTranslation(
                        processedText, langIdResult
                    )
                }

                // Step 5: Render overlays if translation succeeded
                val translationResult = com.aistudio.overread.bzvz.translation.TranslationManager.lastTranslationResult.value
                val metrics = DisplayMetrics()
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)

                if (translationResult != null && translationResult.success && translationResult.blocks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        com.aistudio.overread.bzvz.render.RenderManager.processTranslation(
                            translationResult = translationResult,
                            captureWidth = captureWidth,
                            captureHeight = captureHeight,
                            screenWidth = metrics.widthPixels,
                            screenHeight = metrics.heightPixels
                        )

                        val renderResult = com.aistudio.overread.bzvz.render.RenderManager.lastRenderResult.value
                        if (renderResult != null && renderResult.success && renderResult.boxes.isNotEmpty()) {
                            sendTranslationBroadcast(boxes = renderResult.boxes)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        sendTranslationBroadcast(clear = true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error: ${e.message}")
            isProcessing.set(false)
        }
    }

    /**
     * Convert Image to Bitmap for OCR processing
     */
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmapWidth = width + rowPadding / pixelStride
            if (bitmapWidth <= 0 || height <= 0) return null

            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to exact screen dimensions
            Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap: ${e.message}")
            null
        }
    }

    /**
     * Generate a quick fingerprint from image for change detection
     */
    private fun generateQuickFingerprint(image: Image): Int {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height

            var hash = 0
            val xStep = width / 8
            val yStep = height / 8

            // Sample a 64-point grid
            for (my in 1..7) {
                for (mx in 1..7) {
                    val x = mx * xStep
                    val y = my * yStep
                    val index = y * rowStride + x * pixelStride
                    if (index >= 0 && index < buffer.capacity() - 2) {
                        val r = buffer.get(index).toInt() and 0xFF
                        val g = buffer.get(index + 1).toInt() and 0xFF
                        val b = buffer.get(index + 2).toInt() and 0xFF
                        hash = 31 * hash + (r + g + b)
                    }
                }
            }
            if (hash == 0) 1 else hash
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Send broadcast to show/clear translation overlays
     */
    private fun sendTranslationBroadcast(
        boxes: List<com.aistudio.overread.bzvz.render.RenderTextBox>? = null,
        clear: Boolean = false
    ) {
        val intent = Intent("com.aistudio.overread.TRANSLATION_UPDATE").apply {
            setPackage(packageName)
            if (clear) {
                putExtra("action", "clear")
            } else if (boxes != null) {
                putExtra("action", "show")
                // Send blocks as serialized data
                val boxData = boxes.map {
                    mapOf(
                        "id" to it.id,
                        "text" to it.translatedText,
                        "original" to it.originalText,
                        "left" to it.screenBoundingBox.left.toString(),
                        "top" to it.screenBoundingBox.top.toString(),
                        "right" to it.screenBoundingBox.right.toString(),
                        "bottom" to it.screenBoundingBox.bottom.toString(),
                        "srcLang" to (it.sourceLanguage ?: ""),
                        "tgtLang" to it.targetLanguage
                    )
                }
                putExtra("box_count", boxes.size)
                for ((i, box) in boxes.withIndex()) {
                    putExtra("box_${i}_text", box.translatedText)
                    putExtra("box_${i}_original", box.originalText)
                    putExtra("box_${i}_left", box.screenBoundingBox.left)
                    putExtra("box_${i}_top", box.screenBoundingBox.top)
                    putExtra("box_${i}_right", box.screenBoundingBox.right)
                    putExtra("box_${i}_bottom", box.screenBoundingBox.bottom)
                }
            }
        }
        sendBroadcast(intent)
    }

    private fun cleanupAndStop() {
        if (isCleaningUp) return
        isCleaningUp = true

        stateObserverJob?.cancel()
        frameSampler.clear()
        stabilityDetector.clear()
        lastStableFingerprint = 0

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        // Clear overlays
        sendTranslationBroadcast(clear = true)

        LiveModeController.setState(LiveSessionState.Stopped)
        serviceScope.cancel()

        stopForeground(true)
        stopSelf()
    }

    private suspend fun <T> withTimeout(timeMillis: Long, block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeMillis) {
                block()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Pipeline timed out after ${timeMillis}ms")
            null
        }
    }
}
