package com.screencensor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.screencensor.R
import com.screencensor.databinding.ActivityMainBinding
import com.screencensor.service.ScreenCensorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Permission launcher: Screen Capture (MediaProjection)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCensorService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "จำเป็นต้องอนุญาตการบันทึกหน้าจอเพื่อทำงาน", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher: Overlay Permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkAndStartService()
        } else {
            Toast.makeText(this, "จำเป็นต้องเปิดสิทธิ์ 'แสดงทับแอปอื่น'", Toast.LENGTH_LONG).show()
        }
    }

    // Permission launcher: Notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkAndStartService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupUI() {
        // Slider listener
        binding.sliderConfidence.addOnChangeListener { _, value, _ ->
            binding.tvSensitivity.text = "ความไวในการตรวจจับ (Confidence): ${value.toInt()}%"
        }

        // Toggle button listener
        binding.btnToggle.setOnClickListener {
            if (ScreenCensorService.isRunning) {
                stopCensorService()
            } else {
                checkAndStartService()
            }
        }
    }

    private fun updateUIState() {
        if (ScreenCensorService.isRunning) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnToggle.text = getString(R.string.btn_stop)
            binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            binding.btnToggle.text = getString(R.string.btn_start)
            binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_blue))
        }
    }

    private fun checkAndStartService() {
        // 1. Check Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        // 2. Check Notification Permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // 3. Request Screen Capture Intent
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startCensorService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, ScreenCensorService::class.java).apply {
            putExtra(ScreenCensorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCensorService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCensorService.EXTRA_CONFIDENCE, binding.sliderConfidence.value / 100f)
            putExtra(ScreenCensorService.EXTRA_BREASTS, binding.switchBreasts.isChecked)
            putExtra(ScreenCensorService.EXTRA_GENITALIA, binding.switchGenitalia.isChecked)
            putExtra(ScreenCensorService.EXTRA_BUTTOCKS, binding.switchButtocks.isChecked)
            putExtra(ScreenCensorService.EXTRA_COVERED, binding.switchCovered.isChecked)
        }

        ContextCompat.startForegroundService(this, serviceIntent)
        binding.root.postDelayed({ updateUIState() }, 500)
    }

    private fun stopCensorService() {
        val stopIntent = Intent(this, ScreenCensorService::class.java).apply {
            action = ScreenCensorService.ACTION_STOP
        }
        startService(stopIntent)
        binding.root.postDelayed({ updateUIState() }, 500)
    }
}
