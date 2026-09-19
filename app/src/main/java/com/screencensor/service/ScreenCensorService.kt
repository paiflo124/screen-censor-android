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
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.screencensor.MainActivity
import com.screencensor.R
import com.screencensor.ai.YoloDetector
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
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_BREASTS = "extra_breasts"
        const val EXTRA_GENITALIA = "extra_genitalia"
        const val EXTRA_BUTTOCKS = "extra_buttocks"
        const val EXTRA_COVERED = "extra_covered"
        const val ACTION_STOP = "action_stop_censor"

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
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDpi = 320

    // Capture resolution (scaled down for high performance)
    private val captureWidth = 360
    private val captureHeight = 640

    private var confidenceThreshold = 0.35f
    private var censorBreasts = true
    private var censorGenitalia = true
    private var censorButtocks = true
    private var censorCovered = false

    private var reusableBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        yoloDetector = YoloDetector(applicationContext)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDpi = metrics.densityDpi

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        confidenceThreshold = intent.getFloatExtra(EXTRA_CONFIDENCE, 0.35f)
        censorBreasts = intent.getBooleanExtra(EXTRA_BREASTS, true)
        censorGenitalia = intent.getBooleanExtra(EXTRA_GENITALIA, true)
        censorButtocks = intent.getBooleanExtra(EXTRA_BUTTOCKS, true)
        censorCovered = intent.getBooleanExtra(EXTRA_COVERED, false)

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
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_content))
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

        overlayView = CensorOverlayView(this)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // Key flags: TOUCH-THROUGH and NON-FOCUSABLE
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.i(TAG, "Touch-through Censor Overlay added to WindowManager.")
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

            // RGBA_8888 ImageReader for screen frame capture
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

            // Start detection loop
            startDetectionLoop()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startDetectionLoop() {
        reusableBitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)

        serviceScope.launch {
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

                        // Run YOLOv11 detector
                        val detections = yoloDetector?.detect(
                            bitmap = bitmap,
                            confidenceThreshold = confidenceThreshold,
                            censorBreasts = censorBreasts,
                            censorGenitalia = censorGenitalia,
                            censorButtocks = censorButtocks,
                            censorCovered = censorCovered
                        ) ?: emptyList()

                        // Update overlay view
                        overlayView?.updateDetections(detections)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in detection loop: ${e.message}")
                } finally {
                    image?.close()
                }

                // Maintain ~20 FPS (every 50ms) to ensure smooth censorship without heating the device
                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = (50 - elapsed).coerceAtLeast(10)
                delay(sleepTime)
            }
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

            reusableBitmap?.recycle()
            reusableBitmap = null

            Log.i(TAG, "ScreenCensorService destroyed and resources released.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service teardown: ${e.message}", e)
        }
    }
}
