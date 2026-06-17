package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.data.AppDatabase
import com.example.data.CountdownEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CountdownWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Query database on a background coroutine
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val allEventsFlow = db.eventDao().getAllEvents()
            val allEvents = allEventsFlow.firstOrNull() ?: emptyList()

            // Find pinned event, or first event, or null
            val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, activeEvent)
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
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val allEvents = db.eventDao().getAllEvents().firstOrNull() ?: emptyList()
            val activeEvent = allEvents.firstOrNull { it.isPinned } ?: allEvents.firstOrNull()
            updateWidget(context, appWidgetManager, appWidgetId, activeEvent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Whenever a manual trigger occurs, update all widgets
        if (intent.action == ACTION_UPDATE_WIDGET_DATA) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CountdownWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, allWidgetIds)
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
        val configIntent = Intent(context, MainActivity::class.java)
        val configPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent)

        // Instruct widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET_DATA = "com.example.countdown.UPDATE_WIDGET"

        fun triggerWidgetUpdate(context: Context) {
            val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET_DATA
            }
            context.sendBroadcast(intent)
        }
    }
}
