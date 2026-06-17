package com.example

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import com.example.data.AppDatabase
import com.example.data.CountdownEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.lang.ref.WeakReference

class CountdownNotificationService : Service() {

    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null
    private var updateJob: Job? = null

    companion object {
        const val CHANNEL_ID = "countdown_premium_bar"
        const val NOTIFICATION_ID = 9988
        const val ACTION_START = "com.example.countdown.START"
        const val ACTION_STOP = "com.example.countdown.STOP"
        const val ACTION_UPDATE = "com.example.countdown.UPDATE"

        fun startService(context: Context) {
            val intent = Intent(context.applicationContext, CountdownNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context.applicationContext, CountdownNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.applicationContext.startService(intent)
        }

        fun triggerUpdate(context: Context) {
            val intent = Intent(context.applicationContext, CountdownNotificationService::class.java).apply {
                action = ACTION_UPDATE
            }
            context.applicationContext.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize dynamic SupervisorJob to prevent canceled job reuse issues
        val job = SupervisorJob()
        serviceJob = job
        serviceScope = CoroutineScope(Dispatchers.Main + job)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Fast low-overhead foreground initialization
        val initialNoti = buildPlaceholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID, 
                    initialNoti, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, initialNoti)
            }
        } else {
            startForeground(NOTIFICATION_ID, initialNoti)
        }

        startUpdateCycle()

        return START_STICKY
    }

    private fun startUpdateCycle() {
        updateJob?.cancel()
        val scope = serviceScope ?: return
        updateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                updateNotificationContent()
                delay(30000) // Precise 30-sec interval to sync widget & notification bar 
            }
        }
    }

    private suspend fun updateNotificationContent() {
        val appContext = applicationContext ?: return
        val contextRef = WeakReference(appContext)

        withContext(Dispatchers.IO) {
            val refContext = contextRef.get() ?: return@withContext
            try {
                val db = AppDatabase.getDatabase(refContext)
                val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                val notificationManager = refContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null) {
                    val updatedNoti = buildCustomNotification(refContext, activeEvent)
                    notificationManager.notify(NOTIFICATION_ID, updatedNoti)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildPlaceholderNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return builder
            .setContentTitle("Countdown Bar Active")
            .setContentText("Tap to open application")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildCustomNotification(context: Context, event: CountdownEvent?): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // RemoteViews construction inside current layout
        val views = RemoteViews(context.packageName, R.layout.notification_countdown_layout)

        if (event == null) {
            views.setTextViewText(R.id.noti_event_category, "UNIVERSAL DRIFT")
            views.setTextViewText(R.id.noti_event_title, "No Pinned Countdown")
            views.setTextViewText(R.id.noti_countdown_text, "00d : 00h : 00m")
        } else {
            val diff = event.targetTimestamp - System.currentTimeMillis()
            views.setTextViewText(R.id.noti_event_category, event.category.uppercase())
            views.setTextViewText(R.id.noti_event_title, event.title)

            if (diff <= 0) {
                views.setTextViewText(R.id.noti_countdown_text, "Finished")
            } else {
                val days = diff / (24L * 60 * 60 * 1000)
                val hours = (diff / (60L * 60 * 1000)) % 24
                val minutes = (diff / (60000L)) % 60
                views.setTextViewText(
                    R.id.noti_countdown_text,
                    String.format("%02dd : %02dh : %02dm", days, hours, minutes)
                )
            }
        }

        return builder
            .setCustomContentView(views)
            .setCustomHeadsUpContentView(views)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Premium Countdown Live Stream"
            val descriptionText = "Displays real-time notifications for active countdown events in Apple/iOS style"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope?.cancel()
        serviceJob?.cancel()
        updateJob = null
        serviceScope = null
        serviceJob = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
