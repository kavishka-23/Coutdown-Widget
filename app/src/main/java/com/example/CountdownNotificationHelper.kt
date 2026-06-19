package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.CountdownEvent

object CountdownNotificationHelper {
    private const val CHANNEL_ID = "countdown_milestones_channel"
    private const val CHANNEL_NAME = "Countdown Milestones"
    private const val PREFS_NAME = "countdown_notification_prefs"

    fun showMilestoneNotification(context: Context, event: CountdownEvent, milestoneType: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notiKey = "notified_${milestoneType}_${event.id}"
        
        // Prevent duplicate firing of notifications
        if (sharedPrefs.getBoolean(notiKey, false)) return

        // Save that we have notified so we don't spam it
        sharedPrefs.edit().putBoolean(notiKey, true).apply()

        // Create channel
        createNotificationChannel(context)

        val title = when (milestoneType) {
            "24h" -> "24 Hours Left! ⏳"
            "1h" -> "1 Hour Left! ⚡"
            else -> "Event Finished! 🎉"
        }

        val text = when (milestoneType) {
            "24h" -> "\"${event.title}\" is exactly 24 hours away. Get ready!"
            "1h" -> "\"${event.title}\" is starting in 1 hour. Just 60 minutes left!"
            else -> "\"${event.title}\" has completed. The wait is over!"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id * 10 + milestoneType.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Built-in standard icon
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notificationId = event.id * 100 + when (milestoneType) {
            "24h" -> 1
            "1h" -> 2
            else -> 3
        }
        notificationManager?.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Milestone alerts for your saved countdown events"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    // Reset notification states when event target timestamp is edited/reset
    fun resetMilestoneNotifications(context: Context, eventId: Int) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            remove("notified_24h_$eventId")
            remove("notified_1h_$eventId")
            remove("notified_finished_$eventId")
        }.apply()
    }
}
