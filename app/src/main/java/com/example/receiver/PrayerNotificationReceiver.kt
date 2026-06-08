package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.NotificationTone
import com.example.service.SoundGenerator
import com.example.scheduler.PrayerAlarmScheduler

class PrayerNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PRAYER_ALARM = "com.example.action.PRAYER_ALARM"
        const val EXTRA_PRAYER_ID = "EXTRA_PRAYER_ID"
        const val EXTRA_PRAYER_NAME = "EXTRA_PRAYER_NAME"
        const val EXTRA_TONE_ID = "EXTRA_TONE_ID"
        
        const val CHANNEL_ID = "muscat_prayer_channel_v1"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restore alarms after device reboot
            val scheduler = PrayerAlarmScheduler(context)
            scheduler.rescheduleAlarms()
            return
        }

        if (intent.action == ACTION_PRAYER_ALARM) {
            val prayerId = intent.getStringExtra(EXTRA_PRAYER_ID) ?: return
            val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
            val toneId = intent.getStringExtra(EXTRA_TONE_ID) ?: NotificationTone.PEACEFUL_CHIME.id
            
            // Display the Android System Notification
            showNotification(context, prayerId, prayerName, toneId)
            
            // Synthesize and play custom musical chime/bell alert
            val tone = NotificationTone.values().find { it.id == toneId } ?: NotificationTone.PEACEFUL_CHIME
            SoundGenerator.playTone(tone)
        }
    }

    private fun showNotification(context: Context, prayerId: String, prayerName: String, toneId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Muscat Prayer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Muscat daily prayer times"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            prayerId.hashCode(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use a nice notification template
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("It's time for $prayerName in Muscat")
            .setContentText("Observe the $prayerName morning/daily prayer with devotion.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (toneId == NotificationTone.SYSTEM_DEFAULT.id) {
                    setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                }
            }
            .build()
            
        notificationManager.notify(prayerId.hashCode(), notification)
    }
}
