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

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Capture applicationContext in a WeakReference for absolute leak prevention
        val appContextRef = WeakReference(context.applicationContext)
        val pendingResult = goAsync()

        // Create a short-lived dedicated scope with a supervisor job that is discarded/cancelled immediately after work completes
        val workJob = SupervisorJob()
        val workScope = CoroutineScope(Dispatchers.IO + workJob)

        workScope.launch {
            try {
                val appContext = appContextRef.get()
                if (appContext != null) {
                    val db = AppDatabase.getDatabase(appContext)
                    val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                    val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                    for (appWidgetId in appWidgetIds) {
                        updateWidget(appContext, appWidgetManager, appWidgetId, activeEvent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Terminate pending receiver status to release OS resources
                pendingResult?.finish()
                // Explicitly cancel coroutine scope to clean up any suspended operations
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
                    val db = AppDatabase.getDatabase(appContext)
                    val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                    val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()
                    updateWidget(appContext, appWidgetManager, appWidgetId, activeEvent)
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
                        val appWidgetManager = AppWidgetManager.getInstance(appContext)
                        val thisWidget = ComponentName(appContext, CountdownWidgetProvider::class.java)
                        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                        val db = AppDatabase.getDatabase(appContext)
                        val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
                        val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

                        for (appWidgetId in allWidgetIds) {
                            updateWidget(appContext, appWidgetManager, appWidgetId, activeEvent)
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

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        event: CountdownEvent?
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) ?: 180

        val isCompact = minHeight < 100 || minWidth < 180
        val layoutId = if (isCompact) R.layout.countdown_widget_layout_compact else R.layout.countdown_widget_layout
        val views = RemoteViews(context.packageName, layoutId)

        if (event == null) {
            views.setTextViewText(R.id.widget_category, "NO COUNTDOWNS")
            views.setTextViewText(R.id.widget_title, "Tap to add active countdowns")
            if (isCompact) {
                views.setTextViewText(R.id.widget_countdown_text, "00d 00h 00m")
            } else {
                views.setTextViewText(R.id.widget_days, "00")
                views.setTextViewText(R.id.widget_hours, "00")
                views.setTextViewText(R.id.widget_minutes, "00")
                views.setTextViewText(R.id.widget_indicator, "EMPTY")
            }
        } else {
            val diff = event.targetTimestamp - System.currentTimeMillis()
            if (diff <= 0) {
                views.setTextViewText(R.id.widget_category, event.category.uppercase())
                views.setTextViewText(R.id.widget_title, event.title)
                if (isCompact) {
                    views.setTextViewText(R.id.widget_countdown_text, "FINISHED")
                } else {
                    views.setTextViewText(R.id.widget_days, "00")
                    views.setTextViewText(R.id.widget_hours, "00")
                    views.setTextViewText(R.id.widget_minutes, "00")
                    views.setTextViewText(R.id.widget_indicator, "FINISHED")
                }
            } else {
                val days = diff / (24L * 60 * 60 * 1000)
                val hours = (diff / (60L * 60 * 1000)) % 24
                val minutes = (diff / (60000L)) % 60

                views.setTextViewText(R.id.widget_category, event.category.uppercase())
                views.setTextViewText(R.id.widget_title, event.title)
                if (isCompact) {
                    views.setTextViewText(R.id.widget_countdown_text, "${days}d ${hours}h ${minutes}m")
                } else {
                    views.setTextViewText(R.id.widget_days, String.format("%02d", days))
                    views.setTextViewText(R.id.widget_hours, String.format("%02d", hours))
                    views.setTextViewText(R.id.widget_minutes, String.format("%02d", minutes))
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

    companion object {
        const val ACTION_UPDATE_WIDGET_DATA = "com.example.countdown.UPDATE_WIDGET"

        fun triggerWidgetUpdate(context: Context) {
            val intent = Intent(context.applicationContext, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET_DATA
            }
            context.applicationContext.sendBroadcast(intent)
        }
    }
}
