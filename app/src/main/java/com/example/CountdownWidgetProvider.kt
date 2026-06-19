package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.data.AppDatabase
import com.example.data.CountdownEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleRepeatingUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelRepeatingUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleRepeatingUpdate(context)

        val appContextRef = WeakReference(context.applicationContext)
        val pendingResult = goAsync()

        val workJob = SupervisorJob()
        val workScope = CoroutineScope(Dispatchers.IO + workJob)

        workScope.launch {
            try {
                val appContext = appContextRef.get()
                if (appContext != null) {
                    val sharedPrefs = appContext.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
                    val isDarkMode = sharedPrefs.getBoolean("is_dark_mode", true)

                    val db = AppDatabase.getDatabase(appContext)
                    val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                    val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                    for (appWidgetId in appWidgetIds) {
                        updateWidgetStatic(appContext, appWidgetManager, appWidgetId, activeEvent, isDarkMode)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult?.finish()
                workScope.cancel()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        
        val appContextRef = WeakReference(context.applicationContext)
        val pendingResult = goAsync()

        val workJob = SupervisorJob()
        val workScope = CoroutineScope(Dispatchers.IO + workJob)

        workScope.launch {
            try {
                val appContext = appContextRef.get()
                if (appContext != null) {
                    val sharedPrefs = appContext.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
                    val isDarkMode = sharedPrefs.getBoolean("is_dark_mode", true)

                    val db = AppDatabase.getDatabase(appContext)
                    val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                    val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                    updateWidgetStatic(appContext, appWidgetManager, appWidgetId, activeEvent, isDarkMode)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult?.finish()
                workScope.cancel()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET_DATA) {
            val appContextRef = WeakReference(context.applicationContext)
            val pendingResult = goAsync()

            val workJob = SupervisorJob()
            val workScope = CoroutineScope(Dispatchers.IO + workJob)

            workScope.launch {
                try {
                    val appContext = appContextRef.get()
                    if (appContext != null) {
                        val sharedPrefs = appContext.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
                        val isDarkMode = sharedPrefs.getBoolean("is_dark_mode", true)

                        val appWidgetManager = AppWidgetManager.getInstance(appContext)
                        val thisWidget = ComponentName(appContext, CountdownWidgetProvider::class.java)
                        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                        val db = AppDatabase.getDatabase(appContext)
                        val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                        val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                        for (appWidgetId in allWidgetIds) {
                            updateWidgetStatic(appContext, appWidgetManager, appWidgetId, activeEvent, isDarkMode)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                    workScope.cancel()
                }
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET_DATA = "com.example.countdown.UPDATE_WIDGET"

        fun triggerWidgetUpdate(context: Context) {
            val intent = Intent(context.applicationContext, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET_DATA
            }
            context.applicationContext.sendBroadcast(intent)
        }

        fun scheduleRepeatingUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context.applicationContext, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET_DATA
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + 60000L
            alarmManager.setRepeating(
                android.app.AlarmManager.RTC,
                triggerAt,
                60000L,
                pendingIntent
            )
        }

        fun cancelRepeatingUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context.applicationContext, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET_DATA
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }

        fun updateWidgetStatic(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            event: CountdownEvent?,
            isDarkMode: Boolean
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) ?: 180

            val isCompact = minHeight < 100 || minWidth < 180
            val layoutId = if (isCompact) R.layout.countdown_widget_layout_compact else R.layout.countdown_widget_layout
            val views = RemoteViews(context.packageName, layoutId)

            // Dynamic theme resource resolution
            val rootBgRes = if (isDarkMode) R.drawable.bg_glass_widget else R.drawable.bg_glass_widget_light
            val podBgRes = if (isDarkMode) R.drawable.bg_pod else R.drawable.bg_pod_light

            // Dynamic text color hexes
            val textPrimaryColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt() // Slate 900
            val textSecondaryColor = if (isDarkMode) 0xE6FFFFFF.toInt() else 0xFF334155.toInt() // Slate 700
            val textMutedColor = if (isDarkMode) 0x99FFFFFF.toInt() else 0xFF475569.toInt() // Slate 600 opaque
            val textAccentColor = if (isDarkMode) 0xFF818CF8.toInt() else 0xFF0369A1.toInt() // Sky 700

            views.setInt(R.id.widget_root, "setBackgroundResource", rootBgRes)
            views.setTextColor(R.id.widget_category, textMutedColor)
            views.setTextColor(R.id.widget_title, textPrimaryColor)

            if (isCompact) {
                views.setInt(R.id.widget_pod_container, "setBackgroundResource", podBgRes)
                views.setTextColor(R.id.widget_countdown_text, textPrimaryColor)
            } else {
                views.setInt(R.id.widget_days_container, "setBackgroundResource", podBgRes)
                views.setInt(R.id.widget_hours_container, "setBackgroundResource", podBgRes)
                views.setInt(R.id.widget_minutes_container, "setBackgroundResource", podBgRes)
                views.setInt(R.id.widget_seconds_container, "setBackgroundResource", podBgRes)

                views.setTextColor(R.id.widget_indicator, textAccentColor)

                views.setTextColor(R.id.widget_days, textPrimaryColor)
                views.setTextColor(R.id.widget_hours, textPrimaryColor)
                views.setTextColor(R.id.widget_minutes, textPrimaryColor)
                views.setTextColor(R.id.widget_seconds, textPrimaryColor)

                views.setTextColor(R.id.widget_days_lbl, textMutedColor)
                views.setTextColor(R.id.widget_hours_lbl, textMutedColor)
                views.setTextColor(R.id.widget_minutes_lbl, textMutedColor)
                views.setTextColor(R.id.widget_seconds_lbl, textMutedColor)
            }

            if (event == null) {
                views.setTextViewText(R.id.widget_category, "NO COUNTDOWNS")
                views.setTextViewText(R.id.widget_title, "Tap to add active countdowns")
                if (isCompact) {
                    views.setTextViewText(R.id.widget_countdown_text, "00d 00h 00m 00s")
                } else {
                    views.setTextViewText(R.id.widget_days, "00")
                    views.setTextViewText(R.id.widget_hours, "00")
                    views.setTextViewText(R.id.widget_minutes, "00")
                    views.setTextViewText(R.id.widget_seconds, "00")
                    views.setTextViewText(R.id.widget_indicator, "EMPTY")
                }
            } else {
                val diff = event.targetTimestamp - System.currentTimeMillis()
                views.setTextViewText(R.id.widget_category, event.category.uppercase())
                views.setTextViewText(R.id.widget_title, event.title)

                if (diff <= 0) {
                    if (isCompact) {
                        views.setTextViewText(R.id.widget_countdown_text, "FINISHED")
                    } else {
                        views.setTextViewText(R.id.widget_days, "00")
                        views.setTextViewText(R.id.widget_hours, "00")
                        views.setTextViewText(R.id.widget_minutes, "00")
                        views.setTextViewText(R.id.widget_seconds, "00")
                        views.setTextViewText(R.id.widget_indicator, "FINISHED")
                    }
                } else {
                    val days = diff / (24L * 60 * 60 * 1000)
                    val hours = (diff / (60L * 60 * 1000)) % 24
                    val minutes = (diff / 60000L) % 60
                    val seconds = (diff / 1000L) % 60

                    if (isCompact) {
                        views.setTextViewText(R.id.widget_countdown_text, "${days}d ${hours}h ${minutes}m ${seconds}s")
                    } else {
                        views.setTextViewText(R.id.widget_days, String.format("%02d", days))
                        views.setTextViewText(R.id.widget_hours, String.format("%02d", hours))
                        views.setTextViewText(R.id.widget_minutes, String.format("%02d", minutes))
                        views.setTextViewText(R.id.widget_seconds, String.format("%02d", seconds))
                        views.setTextViewText(R.id.widget_indicator, "ACTIVE")
                    }
                }
            }

            // Deep link to MainActivity
            val configIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
