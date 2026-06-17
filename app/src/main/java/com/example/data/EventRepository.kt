package com.example.data

import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {
    val allEvents: Flow<List<CountdownEvent>> = eventDao.getAllEvents()
    val pinnedEvent: Flow<CountdownEvent?> = eventDao.getPinnedEvent()

    suspend fun insert(event: CountdownEvent): Long {
        return eventDao.insertEvent(event)
    }

    suspend fun update(event: CountdownEvent) {
        eventDao.updateEvent(event)
    }

    suspend fun delete(event: CountdownEvent) {
        eventDao.deleteEvent(event)
    }

    suspend fun deleteById(id: Int) {
        eventDao.deleteById(id)
    }

    suspend fun pinEvent(id: Int) {
        eventDao.setPinnedEvent(id)
    }
}
