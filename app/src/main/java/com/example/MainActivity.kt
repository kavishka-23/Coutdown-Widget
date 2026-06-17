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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room database and repository instantiation setup
        val database = AppDatabase.getDatabase(this)
        val repository = EventRepository(database.eventDao())
        val factory = CountdownViewModel.Factory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[CountdownViewModel::class.java]

        // Auto-start notification countdown service if enabled by user
        val sharedPrefs = getSharedPreferences("premium_countdown_prefs", MODE_PRIVATE)
        if (sharedPrefs.getBoolean("noti_bar_countdown", false)) {
            CountdownNotificationService.startService(this)
        }

        setContent {
            MyApplicationTheme {
                CountdownApp(viewModel = viewModel)
            }
        }
    }
}
