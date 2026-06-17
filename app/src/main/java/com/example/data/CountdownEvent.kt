package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "countdown_events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String,
    val targetTimestamp: Long,
    val isPinned: Boolean = false
)
