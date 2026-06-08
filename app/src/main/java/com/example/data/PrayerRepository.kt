package com.example.data

import android.content.Context
import android.content.Intent
import com.example.scheduler.PrayerAlarmScheduler
import com.example.receiver.PrayerNotificationReceiver
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PrayerRepository(
    private val context: Context,
    private val prayerLogDao: PrayerLogDao
) {
    private val calculator = PrayerTimesCalculator()
    private val scheduler = PrayerAlarmScheduler(context)
    private val sharedPrefs = context.getSharedPreferences("muscat_prayers_prefs", Context.MODE_PRIVATE)

    fun getPrayerTimesForDate(date: LocalDate): PrayerTimesCalculator.PrayerTimes {
        return calculator.calculateTimesForDate(date)
    }

    fun getPrayerLogForDate(date: LocalDate): Flow<PrayerLog?> {
        return prayerLogDao.getLogForDate(date.toString())
    }

    suspend fun savePrayerLog(log: PrayerLog) {
        prayerLogDao.insertOrUpdateLog(log)
    }

    fun isNotificationEnabled(type: PrayerType): Boolean {
        // Default Sunrise to silent/disabled, others to true
        val defaultValue = type != PrayerType.SUNRISE
        return sharedPrefs.getBoolean("${type.id}_notification_enabled", defaultValue)
    }

    fun getNotificationTone(type: PrayerType): NotificationTone {
        val toneId = sharedPrefs.getString("${type.id}_notification_tone", NotificationTone.PEACEFUL_CHIME.id)
            ?: NotificationTone.PEACEFUL_CHIME.id
        return NotificationTone.values().find { it.id == toneId } ?: NotificationTone.PEACEFUL_CHIME
    }

    fun setNotificationEnabled(type: PrayerType, enabled: Boolean) {
        sharedPrefs.edit().putBoolean("${type.id}_notification_enabled", enabled).apply()
        scheduler.updateAlarms()
    }

    fun setNotificationTone(type: PrayerType, tone: NotificationTone) {
        sharedPrefs.edit().putString("${type.id}_notification_tone", tone.id).apply()
        scheduler.updateAlarms()
    }

    /**
     * Instantly triggers a system notification and plays the chosen synthesized sound.
     * This provides a responsive, high-fidelity experience without waiting for a scheduled alarm.
     */
    fun triggerImmediateTestNotification(type: PrayerType) {
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_PRAYER_ALARM
            putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_ID, "${type.id}_test")
            putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_NAME, "${type.displayName} (Test)")
            putExtra(PrayerNotificationReceiver.EXTRA_TONE_ID, getNotificationTone(type).id)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Force alarm scheduling refresh (e.g. at app first startup)
     */
    fun refreshAlarms() {
        scheduler.updateAlarms()
    }

    fun getSelectedTheme(): AppTheme {
        val themeId = sharedPrefs.getString("selected_theme", AppTheme.TWILIGHT_VELVET.id)
            ?: AppTheme.TWILIGHT_VELVET.id
        return AppTheme.values().find { it.id == themeId } ?: AppTheme.TWILIGHT_VELVET
    }

    fun setSelectedTheme(theme: AppTheme) {
        sharedPrefs.edit().putString("selected_theme", theme.id).apply()
    }
}
