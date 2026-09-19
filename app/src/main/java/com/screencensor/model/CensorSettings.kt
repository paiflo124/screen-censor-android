package com.screencensor.model

import android.content.Context
import android.content.SharedPreferences

enum class CensorStyle(val title: String, val desc: String) {
    GLITCH("Cyberpunk Glitch", "เส้นสแกนดิจิทัล + ขอบไฟนีออน ฟ้า-ชมพู"),
    CAUTION_TAPE("Caution Tape", "แถบเทปลายสะท้อนแสง เหลือง-ดำ เอียง 45°"),
    MOSAIC("Japanese Mosaic", "เซ็นเซอร์ตารางพิกเซลสไตล์ญี่ปุ่น"),
    BLUR("Frosted Blur", "กระจกฝ้าเบลอนุ่มนวล กลืนกับหน้าจอ"),
    SOLID_GLOW("Solid Neon", "กล่องดำสนิทขอบไฟนีออนกะพริบตามจังหวะ")
}

data class CensorConfig(
    var style: CensorStyle = CensorStyle.GLITCH,
    var showText: Boolean = true,
    var phrases: List<String> = listOf("CENSORED", "BLOCKED", "NO PEEKING", "FORBIDDEN", "DENIED"),
    var boxPaddingPercent: Float = 0.15f,     // 0% to 50% expansion
    var confidenceThreshold: Float = 0.35f,
    var smoothTracking: Boolean = true,
    var hapticFeedback: Boolean = true,
    var fpsLimit: Int = 25,                    // 15 (Eco), 25 (Normal), 40 (Turbo)
    // Categories
    var censorBreastsExposed: Boolean = true,
    var censorBreastsCovered: Boolean = true,  // Bikini / Underwear
    var censorGenitaliaExposed: Boolean = true,
    var censorGenitaliaCovered: Boolean = true,
    var censorButtocksExposed: Boolean = true,
    var censorButtocksCovered: Boolean = true,
    var censorEyes: Boolean = true,           // Black eye bar
    var censorFace: Boolean = false,
    var censorBelly: Boolean = false,
    var censorArmpits: Boolean = false,
    var censorFeet: Boolean = false
)

object CensorPreferences {
    private const val PREFS_NAME = "screen_censor_prefs"

    fun load(context: Context): CensorConfig {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val styleName = sp.getString("style", CensorStyle.GLITCH.name) ?: CensorStyle.GLITCH.name
        val style = try { CensorStyle.valueOf(styleName) } catch (e: Exception) { CensorStyle.GLITCH }

        return CensorConfig(
            style = style,
            showText = sp.getBoolean("show_text", true),
            phrases = sp.getStringSet("phrases", setOf("CENSORED", "BLOCKED", "NO PEEKING", "FORBIDDEN", "DENIED"))?.toList()
                ?: listOf("CENSORED", "BLOCKED", "NO PEEKING", "FORBIDDEN", "DENIED"),
            boxPaddingPercent = sp.getFloat("box_padding", 0.15f),
            confidenceThreshold = sp.getFloat("confidence", 0.35f),
            smoothTracking = sp.getBoolean("smooth_tracking", true),
            hapticFeedback = sp.getBoolean("haptic_feedback", true),
            fpsLimit = sp.getInt("fps_limit", 25),
            censorBreastsExposed = sp.getBoolean("c_breasts_exposed", true),
            censorBreastsCovered = sp.getBoolean("c_breasts_covered", true),
            censorGenitaliaExposed = sp.getBoolean("c_genitalia_exposed", true),
            censorGenitaliaCovered = sp.getBoolean("c_genitalia_covered", true),
            censorButtocksExposed = sp.getBoolean("c_buttocks_exposed", true),
            censorButtocksCovered = sp.getBoolean("c_buttocks_covered", true),
            censorEyes = sp.getBoolean("c_eyes", true),
            censorFace = sp.getBoolean("c_face", false),
            censorBelly = sp.getBoolean("c_belly", false),
            censorArmpits = sp.getBoolean("c_armpits", false),
            censorFeet = sp.getBoolean("c_feet", false)
        )
    }

    fun save(context: Context, config: CensorConfig) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().apply {
            putString("style", config.style.name)
            putBoolean("show_text", config.showText)
            putStringSet("phrases", config.phrases.toSet())
            putFloat("box_padding", config.boxPaddingPercent)
            putFloat("confidence", config.confidenceThreshold)
            putBoolean("smooth_tracking", config.smoothTracking)
            putBoolean("haptic_feedback", config.hapticFeedback)
            putInt("fps_limit", config.fpsLimit)
            putBoolean("c_breasts_exposed", config.censorBreastsExposed)
            putBoolean("c_breasts_covered", config.censorBreastsCovered)
            putBoolean("c_genitalia_exposed", config.censorGenitaliaExposed)
            putBoolean("c_genitalia_covered", config.censorGenitaliaCovered)
            putBoolean("c_buttocks_exposed", config.censorButtocksExposed)
            putBoolean("c_buttocks_covered", config.censorButtocksCovered)
            putBoolean("c_eyes", config.censorEyes)
            putBoolean("c_face", config.censorFace)
            putBoolean("c_belly", config.censorBelly)
            putBoolean("c_armpits", config.censorArmpits)
            putBoolean("c_feet", config.censorFeet)
            apply()
        }
    }
}
