package com.example.data

import java.time.LocalTime

enum class PrayerType(
    val id: String,
    val displayName: String,
    val arabicName: String,
    val description: String,
    val isActualPrayer: Boolean
) {
    FAJR("fajr", "Fajr", "الفجر", "The dawn prayer, observed before sunrise", true),
    SUNRISE("sunrise", "Sunrise", "الشروق", "The moment the sun begins to rise", false),
    DHUHR("dhuhr", "Dhuhr", "الظهر", "The midday prayer, observed after solar noon", true),
    ASR("asr", "Asr", "العصر", "The afternoon prayer", true),
    MAGHRIB("maghrib", "Maghrib", "المغرب", "The sunset prayer, observed immediately after sunset", true),
    ISHA("isha", "Isha", "العشاء", "The night prayer, observed after twilight fades", true)
}

data class PrayerItem(
    val type: PrayerType,
    val time: LocalTime,
    val isEnabled: Boolean,
    val isCompleted: Boolean = false
)

enum class NotificationTone(val id: String, val displayName: String) {
    PEACEFUL_CHIME("peaceful_chime", "Peaceful Chime 🔔"),
    RESONANT_GONG("resonant_gong", "Resonant Gong 🪘"),
    STANDARD_BEEP("standard_beep", "Standard Beep 📟"),
    SYSTEM_DEFAULT("system_default", "System Default 🎵"),
    SILENT("silent", "Silent / Vibration Only 📳")
}

enum class AppTheme(
    val id: String,
    val displayName: String,
    val description: String,
    val startColor: Long,
    val midColor: Long,
    val endColor: Long,
    val primaryAccent: Long,
    val cardBgColor: Long
) {
    TWILIGHT_VELVET(
        "twilight_velvet",
        "Twilight Velvet 🌌",
        "Deep night indigo with warm golden sunset accents",
        0xFF070B18L,
        0xFF0F1530L,
        0xFF1E163BL,
        0xFFFFD54FL,
        0x15FFFFFFL
    ),
    ROYAL_EMERALD(
        "royal_emerald",
        "Royal Emerald 🕌",
        "Imperial green with brass-bronze ivory accents",
        0xFF03160CL,
        0xFF072917L,
        0xFF0B3A21L,
        0xFFE0C068L,
        0x15FFFFFFL
    ),
    MIDNIGHT_STAR(
        "midnight_star",
        "Midnight Star 🌠",
        "Pure slate night sky with brilliant cosmic cyan accents",
        0xFF050505L,
        0xFF121212L,
        0xFF1C1D24L,
        0xFF4FC3F7L,
        0x1CFFFFFFL
    ),
    SAHARA_DAWN(
        "sahara_dawn",
        "Sahara Dawn 🏜️",
        "Warm copper sunset with rich saffron amber accents",
        0xFF240A12L,
        0xFF3E1416L,
        0xFF5C2612L,
        0xFFFFB74DL,
        0x18FFFFFFL
    )
}
