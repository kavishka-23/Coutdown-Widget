package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CountdownEvent
import com.example.data.EventRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class CountdownViewModel(
    application: Application,
    private val repository: EventRepository
) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)

    // Dark Mode Theme State (default: true for deep space dark vibe)
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Active tick trigger to refresh countdown timer calculation dynamic state
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private var timerJob: Job? = null

    // Get all events
    val allEvents: StateFlow<List<CountdownEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Get the currently pinned event or fallback to the first event if none is pinned
    val pinnedEvent: StateFlow<CountdownEvent?> = repository.pinnedEvent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        startTimer()
        seedDefaultDataIfEmpty()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _currentTime.value = now

                // Check event target milestones to fire standard system notification banners
                allEvents.value.forEach { event ->
                    val diff = event.targetTimestamp - now
                    if (diff > 0) {
                        // 24 Hour Milestone check (range of 23.5h to 24.5h)
                        if (diff in 84600000L..88200000L) {
                            com.example.CountdownNotificationHelper.showMilestoneNotification(getApplication(), event, "24h")
                        }
                        // 1 Hour Milestone check (range of 55m to 65m)
                        if (diff in 3300000L..3900000L) {
                            com.example.CountdownNotificationHelper.showMilestoneNotification(getApplication(), event, "1h")
                        }
                    } else {
                        // Finished/Reached zero check (within 2 minutes of crossing)
                        if (diff >= -120000L) {
                            com.example.CountdownNotificationHelper.showMilestoneNotification(getApplication(), event, "finished")
                        }
                    }
                }

                // Push dynamic clock updates live of seconds directly to the home screen widgets
                com.example.CountdownWidgetProvider.triggerWidgetUpdate(getApplication())

                delay(1000)
            }
        }
    }

    private fun seedDefaultDataIfEmpty() {
        viewModelScope.launch {
            // Check if db is empty
            repository.allEvents.first().let { currentList ->
                if (currentList.isEmpty()) {
                    val now = System.currentTimeMillis()
                    // Seed Mars Base Landing (14 days, 8 hours, 42 minutes from now)
                    val marsTimestamp = now + (14L * 24 * 60 * 60 * 1000) + (8L * 60 * 60 * 1000) + (42L * 60 * 1000)
                    val marsId = repository.insert(
                        CountdownEvent(
                            title = "MARS BASE LANDING",
                            category = "COSMOS EXPEDITION II",
                            targetTimestamp = marsTimestamp,
                            isPinned = true
                        )
                    )

                    // Seed Tokyo Flight (3 days, 12 hours from now)
                    val tokyoTimestamp = now + (3L * 24 * 60 * 60 * 1000) + (12L * 60 * 60 * 1000)
                    repository.insert(
                        CountdownEvent(
                            title = "Tokyo Flight",
                            category = "Upcoming",
                            targetTimestamp = tokyoTimestamp,
                            isPinned = false
                        )
                    )
                }
            }
        }
    }

    fun toggleDarkMode() {
        val newVal = !_isDarkMode.value
        _isDarkMode.value = newVal
        sharedPrefs.edit().putBoolean("is_dark_mode", newVal).apply()
    }

    fun addEvent(title: String, category: String, targetTimestamp: Long) {
        viewModelScope.launch {
            val isFirst = repository.allEvents.first().isEmpty()
            val event = CountdownEvent(
                title = title,
                category = category,
                targetTimestamp = targetTimestamp,
                isPinned = isFirst // pin if it's the first
            )
            val newId = repository.insert(event)
            com.example.CountdownNotificationHelper.resetMilestoneNotifications(getApplication(), newId.toInt())
            // Trigger sync immediately
            com.example.CountdownWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun updateEvent(event: CountdownEvent) {
        viewModelScope.launch {
            repository.update(event)
            com.example.CountdownNotificationHelper.resetMilestoneNotifications(getApplication(), event.id)
            // Trigger sync immediately
            com.example.CountdownWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun deleteEvent(event: CountdownEvent) {
        viewModelScope.launch {
            repository.delete(event)
            // If the deleted event was pinned, select another event to pin
            if (event.isPinned) {
                val remaining = repository.allEvents.first()
                val nextToPin = remaining.firstOrNull { it.id != event.id }
                if (nextToPin != null) {
                    repository.pinEvent(nextToPin.id)
                }
            }
            // Trigger sync immediately
            com.example.CountdownWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    fun selectPinnedEvent(eventId: Int) {
        viewModelScope.launch {
            repository.pinEvent(eventId)
            // Trigger sync immediately
            com.example.CountdownWidgetProvider.triggerWidgetUpdate(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // Factory Class for CountdownsViewModel
    class Factory(
        private val application: Application,
        private val repository: EventRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CountdownViewModel::class.java)) {
                return CountdownViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
