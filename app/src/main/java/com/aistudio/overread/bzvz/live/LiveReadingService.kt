package com.aistudio.overread.bzvz.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aistudio.overread.bzvz.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class LiveReadingService : Service() {
    private val CHANNEL_ID = "LiveReadingServiceChannel"
    private val NOTIFICATION_ID = 200

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var isCleaningUp = false
    private val frameSampler = FrameSampler()
    private val stabilityDetector = StabilityDetector()

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_LIVE_READING") {
            LiveModeController.stop()
            cleanupAndStop()
            return START_NOT_STICKY
        }
        
        if (intent?.action == "PAUSE_LIVE_READING") {
            LiveModeController.pause()
            return START_NOT_STICKY
        }

        if (intent?.action == "RESUME_LIVE_READING") {
            LiveModeController.resume()
            return START_NOT_STICKY
        }

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

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Reading Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for Live Reading mode"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, LiveReadingService::class.java).apply {
            action = "STOP_LIVE_READING"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val pauseIntent = Intent(this, LiveReadingService::class.java).apply {
            action = "PAUSE_LIVE_READING"
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resumeIntent = Intent(this, LiveReadingService::class.java).apply {
            action = "RESUME_LIVE_READING"
        }
        val resumePendingIntent = PendingIntent.getService(
            this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OverRead Live Reading is active")
            .setContentText("Screen sharing is active. OverRead analyzes frames only while Live Reading is on.")
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
                // If user stops via system UI
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

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) {
                val state = LiveModeController.state.value
                val isPaused = (state == LiveSessionState.Paused)
                
                if (state == LiveSessionState.Active || state == LiveSessionState.Paused) {
                    LiveModeController.incrementFrames()
                    
                    val nowMs = System.currentTimeMillis()
                    if (frameSampler.shouldSample(nowMs, isPaused)) {
                        LiveModeController.incrementFramesAccepted()
                        
                        val stability = stabilityDetector.processFrame(image, nowMs)
                        LiveModeController.setStabilityState(stability)

                        if (stability == StabilityState.Stable) {
                            // Phase R4: We just detect stability, no heavy OCR yet.
                            // Phase R5 will trigger OCR here.
                        } else if (stability == StabilityState.Moving) {
                            // Phase R4: Clear overlays? Handled by UI observing LiveModeController.
                        }
                    } else {
                        LiveModeController.incrementFramesDropped()
                    }
                }
                
                // R4 Phase: immediately close image
                image.close()
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
        } catch (e: Exception) {
            LiveModeController.setState(LiveSessionState.Failed)
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        if (isCleaningUp) return
        isCleaningUp = true

        stateObserverJob?.cancel()
        frameSampler.clear()
        stabilityDetector.clear()

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (e: Exception) {}
        imageReader = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null
        
        LiveModeController.clear() // Sets to Idle

        stopForeground(true)
        stopSelf()
    }
}
