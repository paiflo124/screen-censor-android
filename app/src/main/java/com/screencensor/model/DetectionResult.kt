package com.screencensor.model

import android.graphics.RectF

data class DetectionBox(
    val rect: RectF,          // Normalized coordinates (0..1) or screen coordinates
    val classId: Int,
    val className: String,
    val score: Float,
    val category: CensorCategory
)

enum class CensorCategory {
    BREASTS,
    GENITALIA,
    BUTTOCKS,
    COVERED,
    OTHER
}

object YoloLabels {
    val NAMES = arrayOf(
        "FEMALE_FACE",               // 0
        "MALE_FACE",                 // 1
        "FEMALE_GENITALIA_COVERED",  // 2
        "FEMALE_GENITALIA_EXPOSED",  // 3
        "BUTTOCKS_COVERED",          // 4
        "BUTTOCKS_EXPOSED",          // 5
        "FEMALE_BREAST_COVERED",     // 6
        "FEMALE_BREAST_EXPOSED",     // 7
        "MALE_BREAST_EXPOSED",       // 8
        "ARMPITS_EXPOSED",           // 9
        "BELLY_EXPOSED",             // 10
        "MALE_GENITALIA_EXPOSED",    // 11
        "ANUS_EXPOSED",              // 12
        "FEET_COVERED",              // 13
        "FEET_EXPOSED",              // 14
        "EYE"                        // 15
    )

    fun getCategory(classId: Int): CensorCategory {
        return when (classId) {
            7 -> CensorCategory.BREASTS
            3, 11 -> CensorCategory.GENITALIA
            5, 12 -> CensorCategory.BUTTOCKS
            2, 4, 6 -> CensorCategory.COVERED
            else -> CensorCategory.OTHER
        }
    }
}
