package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.EventRepository
import com.example.ui.CountdownApp
import com.example.ui.CountdownViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CountdownViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room database and repository instantiation setup
        val database = AppDatabase.getDatabase(this)
        val repository = EventRepository(database.eventDao())
        val factory = CountdownViewModel.Factory(application, repository)
        viewModel = ViewModelProvider(this, factory)[CountdownViewModel::class.java]

        setContent {
            MyApplicationTheme {
                CountdownApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Start/resume the 1s UI ticker loop when the app is actively on screen
        viewModel.resumeTimer()

        // Reset the task removed flag to false
        val sharedPrefs = getSharedPreferences("countdown_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("app_task_removed", false).apply()

        // Start AppCleanupService to run background widget updates safely
        AppCleanupService.startServiceSafely(this)

        // Reschedule automatic repeating widget updates and refresh them immediately
        CountdownWidgetProvider.scheduleRepeatingUpdate(this)
        CountdownWidgetProvider.triggerWidgetUpdate(this)
    }

    override fun onStop() {
        super.onStop()
        // Stop/pause the ticker loop to conserve battery/RAM when the app goes into the background
        viewModel.pauseTimer()
    }
}
