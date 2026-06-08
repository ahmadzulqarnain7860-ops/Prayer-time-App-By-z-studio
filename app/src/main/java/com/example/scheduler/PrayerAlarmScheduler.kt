package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.NotificationTone
import com.example.data.PrayerTimesCalculator
import com.example.data.PrayerType
import com.example.receiver.PrayerNotificationReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PrayerAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val calculator = PrayerTimesCalculator()

    fun updateAlarms() {
        val sharedPrefs = context.getSharedPreferences("muscat_prayers_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val prayerTimes = calculator.calculateTimesForDate(today)

        val prayersMap = mapOf(
            PrayerType.FAJR to prayerTimes.fajr,
            PrayerType.DHUHR to prayerTimes.dhuhr,
            PrayerType.ASR to prayerTimes.asr,
            PrayerType.MAGHRIB to prayerTimes.maghrib,
            PrayerType.ISHA to prayerTimes.isha
        )

        for ((type, time) in prayersMap) {
            val isEnabled = sharedPrefs.getBoolean("${type.id}_notification_enabled", true)
            val toneId = sharedPrefs.getString("${type.id}_notification_tone", NotificationTone.PEACEFUL_CHIME.id) ?: NotificationTone.PEACEFUL_CHIME.id

            if (isEnabled) {
                scheduleAlarm(type.id, type.displayName, time, toneId)
            } else {
                cancelAlarm(type.id)
            }
        }
    }

    fun rescheduleAlarms() {
        updateAlarms()
    }

    private fun scheduleAlarm(prayerId: String, prayerName: String, time: LocalTime, toneId: String) {
        val today = LocalDate.now()
        val alarmTimeMs = getTargetAlarmTimeMs(time, today)

        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_PRAYER_ALARM
            putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_ID, prayerId)
            putExtra(PrayerNotificationReceiver.EXTRA_PRAYER_NAME, prayerName)
            putExtra(PrayerNotificationReceiver.EXTRA_TONE_ID, toneId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMs,
                    pendingIntent
                )
            }
            Log.d("PrayerAlarmScheduler", "Successfully scheduled alarm for $prayerName at timestamp $alarmTimeMs")
        } catch (e: SecurityException) {
            // Fallback for Android 14+ if strict exact alarm permissions are not granted yet
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMs,
                pendingIntent
            )
            Log.w("PrayerAlarmScheduler", "Exact alarms security exception. Fell back to regular AlarmManager.set", e)
        }
    }

    private fun cancelAlarm(prayerId: String) {
        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            action = PrayerNotificationReceiver.ACTION_PRAYER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("PrayerAlarmScheduler", "Cancelled scheduled alarm for $prayerId")
        }
    }

    private fun getTargetAlarmTimeMs(prayerTime: LocalTime, today: LocalDate): Long {
        val ldtToday = LocalDateTime.of(today, prayerTime)
        val now = LocalDateTime.now()
        val target = if (ldtToday.isBefore(now)) {
            ldtToday.plusDays(1) // Alarm has passed today, schedule for tomorrow
        } else {
            ldtToday
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
