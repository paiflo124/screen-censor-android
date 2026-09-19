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
import com.screencensor.databinding.ActivityMainBinding
import com.screencensor.model.CensorConfig
import com.screencensor.model.CensorPreferences
import com.screencensor.model.CensorStyle
import com.screencensor.service.ScreenCensorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var config: CensorConfig

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCensorService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "จำเป็นต้องอนุญาตการบันทึกหน้าจอเพื่อเริ่มทำงาน", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkAndStartService()
        } else {
            Toast.makeText(this, "จำเป็นต้องเปิดสิทธิ์ 'แสดงทับแอปอื่น'", Toast.LENGTH_LONG).show()
        }
    }

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
        config = CensorPreferences.load(this)

        initUIFromConfig()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun initUIFromConfig() {
        // Style RadioGroup
        when (config.style) {
            CensorStyle.GLITCH -> binding.rbGlitch.isChecked = true
            CensorStyle.CAUTION_TAPE -> binding.rbCautionTape.isChecked = true
            CensorStyle.MOSAIC -> binding.rbMosaic.isChecked = true
            CensorStyle.BLUR -> binding.rbBlur.isChecked = true
            CensorStyle.SOLID_GLOW -> binding.rbSolidGlow.isChecked = true
        }

        // Switches
        binding.switchShowText.isChecked = config.showText
        binding.sliderPadding.value = (config.boxPaddingPercent * 100).coerceIn(0f, 40f)
        binding.tvPadding.text = "ขยายขนาดกล่อง (Box Padding): ${binding.sliderPadding.value.toInt()}%"

        binding.sliderConfidence.value = (config.confidenceThreshold * 100).coerceIn(10f, 80f)
        binding.tvConfidence.text = "ความไวในการตรวจจับ (Confidence): ${binding.sliderConfidence.value.toInt()}%"

        binding.switchBreastsExposed.isChecked = config.censorBreastsExposed
        binding.switchBreastsCovered.isChecked = config.censorBreastsCovered
        binding.switchGenitaliaExposed.isChecked = config.censorGenitaliaExposed
        binding.switchGenitaliaCovered.isChecked = config.censorGenitaliaCovered
        binding.switchButtocksExposed.isChecked = config.censorButtocksExposed
        binding.switchButtocksCovered.isChecked = config.censorButtocksCovered
        binding.switchEyes.isChecked = config.censorEyes
        binding.switchFace.isChecked = config.censorFace
        binding.switchBelly.isChecked = config.censorBelly
        binding.switchArmpits.isChecked = config.censorArmpits

        binding.switchSmooth.isChecked = config.smoothTracking
        binding.switchHaptic.isChecked = config.hapticFeedback
    }

    private fun setupListeners() {
        // Start / Stop Toggle
        binding.btnToggle.setOnClickListener {
            if (ScreenCensorService.isRunning) {
                stopCensorService()
            } else {
                checkAndStartService()
            }
        }

        // Style change
        binding.rgStyles.setOnCheckedChangeListener { _, checkedId ->
            config.style = when (checkedId) {
                R.id.rbCautionTape -> CensorStyle.CAUTION_TAPE
                R.id.rbMosaic -> CensorStyle.MOSAIC
                R.id.rbBlur -> CensorStyle.BLUR
                R.id.rbSolidGlow -> CensorStyle.SOLID_GLOW
                else -> CensorStyle.GLITCH
            }
            saveAndNotify()
        }

        binding.switchShowText.setOnCheckedChangeListener { _, isChecked ->
            config.showText = isChecked
            saveAndNotify()
        }

        binding.sliderPadding.addOnChangeListener { _, value, _ ->
            config.boxPaddingPercent = value / 100f
            binding.tvPadding.text = "ขยายขนาดกล่อง (Box Padding): ${value.toInt()}%"
            saveAndNotify()
        }

        binding.sliderConfidence.addOnChangeListener { _, value, _ ->
            config.confidenceThreshold = value / 100f
            binding.tvConfidence.text = "ความไวในการตรวจจับ (Confidence): ${value.toInt()}%"
            saveAndNotify()
        }

        // Category switches
        binding.switchBreastsExposed.setOnCheckedChangeListener { _, isChecked -> config.censorBreastsExposed = isChecked; saveAndNotify() }
        binding.switchBreastsCovered.setOnCheckedChangeListener { _, isChecked -> config.censorBreastsCovered = isChecked; saveAndNotify() }
        binding.switchGenitaliaExposed.setOnCheckedChangeListener { _, isChecked -> config.censorGenitaliaExposed = isChecked; saveAndNotify() }
        binding.switchGenitaliaCovered.setOnCheckedChangeListener { _, isChecked -> config.censorGenitaliaCovered = isChecked; saveAndNotify() }
        binding.switchButtocksExposed.setOnCheckedChangeListener { _, isChecked -> config.censorButtocksExposed = isChecked; saveAndNotify() }
        binding.switchButtocksCovered.setOnCheckedChangeListener { _, isChecked -> config.censorButtocksCovered = isChecked; saveAndNotify() }
        binding.switchEyes.setOnCheckedChangeListener { _, isChecked -> config.censorEyes = isChecked; saveAndNotify() }
        binding.switchFace.setOnCheckedChangeListener { _, isChecked -> config.censorFace = isChecked; saveAndNotify() }
        binding.switchBelly.setOnCheckedChangeListener { _, isChecked -> config.censorBelly = isChecked; saveAndNotify() }
        binding.switchArmpits.setOnCheckedChangeListener { _, isChecked -> config.censorArmpits = isChecked; saveAndNotify() }

        binding.switchSmooth.setOnCheckedChangeListener { _, isChecked -> config.smoothTracking = isChecked; saveAndNotify() }
        binding.switchHaptic.setOnCheckedChangeListener { _, isChecked -> config.hapticFeedback = isChecked; saveAndNotify() }
    }

    private fun saveAndNotify() {
        CensorPreferences.save(this, config)
        if (ScreenCensorService.isRunning) {
            val updateIntent = Intent(this, ScreenCensorService::class.java).apply {
                action = ScreenCensorService.ACTION_UPDATE_CONFIG
            }
            startService(updateIntent)
        }
    }

    private fun updateUIState() {
        if (ScreenCensorService.isRunning) {
            binding.tvStatus.text = "สถานะ: กำลังสแกนและเซ็นเซอร์ (${config.style.title})"
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
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startCensorService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, ScreenCensorService::class.java).apply {
            putExtra(ScreenCensorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCensorService.EXTRA_RESULT_DATA, resultData)
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
