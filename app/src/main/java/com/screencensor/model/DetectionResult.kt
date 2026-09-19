package com.screencensor.model

import android.graphics.RectF

data class DetectionBox(
    var rect: RectF,          // Normalized coordinates (0..1)
    val classId: Int,
    val className: String,
    val score: Float,
    val category: DetailedCategory,
    var phrase: String = "CENSORED",
    var framesSinceDetected: Int = 0 // For persistence/tracking
)

enum class DetailedCategory {
    BREASTS_EXPOSED,
    BREASTS_COVERED,
    GENITALIA_EXPOSED,
    GENITALIA_COVERED,
    BUTTOCKS_EXPOSED,
    BUTTOCKS_COVERED,
    EYE,
    FACE,
    BELLY,
    ARMPITS,
    FEET,
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

    fun getCategory(classId: Int): DetailedCategory {
        return when (classId) {
            7, 8 -> DetailedCategory.BREASTS_EXPOSED
            6 -> DetailedCategory.BREASTS_COVERED
            3, 11 -> DetailedCategory.GENITALIA_EXPOSED
            2 -> DetailedCategory.GENITALIA_COVERED
            5, 12 -> DetailedCategory.BUTTOCKS_EXPOSED
            4 -> DetailedCategory.BUTTOCKS_COVERED
            15 -> DetailedCategory.EYE
            0, 1 -> DetailedCategory.FACE
            10 -> DetailedCategory.BELLY
            9 -> DetailedCategory.ARMPITS
            13, 14 -> DetailedCategory.FEET
            else -> DetailedCategory.OTHER
        }
    }
}
