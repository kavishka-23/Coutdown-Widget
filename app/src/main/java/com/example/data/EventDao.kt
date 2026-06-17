package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM countdown_events ORDER BY isPinned DESC, targetTimestamp ASC")
    fun getAllEvents(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events WHERE isPinned = 1 LIMIT 1")
    fun getPinnedEvent(): Flow<CountdownEvent?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CountdownEvent): Long

    @Update
    suspend fun updateEvent(event: CountdownEvent)

    @Delete
    suspend fun deleteEvent(event: CountdownEvent)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE countdown_events SET isPinned = 0")
    suspend fun unpinAllEvents()

    @Transaction
    suspend fun setPinnedEvent(eventId: Int) {
        unpinAllEvents()
        unpinAllEventsInDb() // Backup query if needed, but a single update that sets isPinned = 0 for all is sufficient.
        pinEvent(eventId)
    }

    @Query("UPDATE countdown_events SET isPinned = 0")
    suspend fun unpinAllEventsInDb()

    @Query("UPDATE countdown_events SET isPinned = 1 WHERE id = :eventId")
    suspend fun pinEvent(eventId: Int)
}
