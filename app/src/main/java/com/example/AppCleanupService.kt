package com.example

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppCleanupService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var tickerJob: Job? = null

    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    startTicker()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopTicker()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Check initial screen state to avoid redundant work
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = pm?.isInteractive ?: true

        // Register dynamic screen status receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Start the 1s widget ticker if the screen is currently active
        if (isScreenOn) {
            startTicker()
        }
        
        // Ensure app_task_removed is false so widget is active
        val sharedPrefs = getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("app_task_removed", false).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure starting ticking
        if (isScreenOn) {
            startTicker()
        }
        return START_STICKY
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                // Highly efficient low CPU overhead update trigger
                CountdownWidgetProvider.triggerWidgetUpdate(this@AppCleanupService)
                delay(1000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // DO NOT stop the service or cancel updates when task removed
        // Let START_STICKY keep it running, and make sure it doesn't flag as paused
        val sharedPrefs = getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("app_task_removed", false).apply()
        
        // Also ensure widget keeps updating
        if (isScreenOn) {
            startTicker()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Safe fallback
        }
        stopTicker()
        job.cancel()
        super.onDestroy()
    }

    companion object {
        fun startServiceSafely(context: Context) {
            try {
                val intent = Intent(context.applicationContext, AppCleanupService::class.java)
                context.applicationContext.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
