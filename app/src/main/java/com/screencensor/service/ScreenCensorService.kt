package com.screencensor.service

import android.app.Notification
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
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.screencensor.MainActivity
import com.screencensor.R
import com.screencensor.ai.YoloDetector
import com.screencensor.model.CensorConfig
import com.screencensor.model.CensorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class ScreenCensorService : Service() {

    companion object {
        private const val TAG = "ScreenCensorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_censor_channel"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val ACTION_STOP = "action_stop_censor"
        const val ACTION_UPDATE_CONFIG = "action_update_config"

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: CensorOverlayView? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var yoloDetector: YoloDetector? = null
    private val boxTracker = BoxTracker()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDpi = 320

    private val captureWidth = 360
    private val captureHeight = 640

    private var config: CensorConfig = CensorConfig()
    private var reusableBitmap: Bitmap? = null

    private var vibrator: Vibrator? = null
    private var lastHadDetections = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        yoloDetector = YoloDetector(applicationContext)

        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDpi = metrics.densityDpi

        config = CensorPreferences.load(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_UPDATE_CONFIG) {
            config = CensorPreferences.load(this)
            overlayView?.config = config
            Log.i(TAG, "Config reloaded live: Style=${config.style}, Text=${config.showText}")
            return START_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        config = CensorPreferences.load(this)

        if (resultCode != 0 && resultData != null) {
            startForegroundServiceNotification()
            setupOverlayWindow()
            startScreenCapture(resultCode, resultData)
            isRunning = true
        } else {
            Log.e(TAG, "Missing MediaProjection credentials.")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCensorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Censor Super App")
            .setContentText("กำลังตรวจจับแบบเรียลไทม์ (${config.style.title})")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen Censor Active Notification"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupOverlayWindow() {
        if (overlayView != null) return

        overlayView = CensorOverlayView(this).apply {
            this.config = this@ScreenCensorService.config
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.i(TAG, "Touch-through Censor Overlay View attached.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view: ${e.message}", e)
        }
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        try {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection could not be acquired.")
                stopSelf()
                return
            }

            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCensorCapture",
                captureWidth,
                captureHeight,
                screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            startDetectionLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startDetectionLoop() {
        reusableBitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)

        serviceScope.launch {
            val targetInterval = (1000 / config.fpsLimit.coerceIn(15, 60)).toLong()

            while (isActive && isRunning) {
                val startTime = System.currentTimeMillis()
                var image: Image? = null
                try {
                    image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * captureWidth

                        val bitmap = reusableBitmap ?: Bitmap.createBitmap(
                            captureWidth + rowPadding / pixelStride,
                            captureHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)

                        // 1. Run AI Detection
                        val rawDetections = yoloDetector?.detect(bitmap, config) ?: emptyList()

                        // 2. Apply Box Smoothing & Persistence Tracker
                        val smoothedDetections = boxTracker.update(rawDetections, config.smoothTracking)

                        // 3. Haptic Pulse on new block trigger
                        if (config.hapticFeedback && rawDetections.isNotEmpty() && !lastHadDetections) {
                            triggerHapticPulse()
                        }
                        lastHadDetections = rawDetections.isNotEmpty()

                        // 4. Update Overlay
                        overlayView?.updateDetections(smoothedDetections)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in detection loop: ${e.message}")
                } finally {
                    image?.close()
                }

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = (targetInterval - elapsed).coerceAtLeast(5)
                delay(sleepTime)
            }
        }
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Haptic error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()

        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            mediaProjection?.stop()
            mediaProjection = null

            yoloDetector?.close()
            yoloDetector = null

            boxTracker.clear()

            reusableBitmap?.recycle()
            reusableBitmap = null

            Log.i(TAG, "ScreenCensorService destroyed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service teardown: ${e.message}", e)
        }
    }
}
