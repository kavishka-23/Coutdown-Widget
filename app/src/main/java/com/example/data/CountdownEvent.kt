package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "countdown_events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String,
    val targetTimestamp: Long,
    val isPinned: Boolean = false,
    val isRepeating: Boolean = false,
    val repeatStartDate: Long = 0L,
    val repeatEndDate: Long = 0L,
    val repeatDayOfWeek: Int = -1, // Calendar.SUNDAY(1) to Calendar.SATURDAY(7)
    val repeatHour: Int = 0,
    val repeatMinute: Int = 0,
    val repeatAmPm: String = "AM"
)
