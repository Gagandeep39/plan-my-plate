package com.planmyplate.data.repository

import com.planmyplate.data.MealDao
import com.planmyplate.model.Dish
import com.planmyplate.model.MealCalendarMapping
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.flow.Flow

class MealRepository(
    private val mealDao: MealDao,
    private val calendarRepository: CalendarRepository
) {
    fun getAllMeals(): Flow<List<MealWithDishes>> = mealDao.getAllMeals()

    suspend fun saveMeal(session: MealSession, dishes: List<String>): Long {
        // Insert or update the session first to get/confirm the sessionId
        val sessionId = mealDao.insertSession(session)
        
        // Sync with Google Calendar if connected
        val existingEventId = mealDao.getCalendarEventId(sessionId)
        
        val newEventId = if (existingEventId == null) {
            calendarRepository.createEvent(session.copy(sessionId = sessionId), dishes)
        } else {
            calendarRepository.updateEvent(existingEventId, session.copy(sessionId = sessionId), dishes)
            existingEventId
        }
        
        if (newEventId != null) {
            mealDao.insertCalendarMapping(MealCalendarMapping(sessionId, newEventId))
        }

        // Simple syncing logic: insert current dishes
        val dishesToInsert = dishes.map { dishName ->
            Dish(parentSessionId = sessionId, dishName = dishName)
        }
        if (dishesToInsert.isNotEmpty()) {
            mealDao.insertDishes(dishesToInsert)
        }
        return sessionId
    }

    suspend fun deleteMeal(session: MealSession) {
        val eventId = mealDao.getCalendarEventId(session.sessionId)
        eventId?.let {
            calendarRepository.deleteEvent(it)
            mealDao.deleteCalendarMapping(session.sessionId)
        }
        mealDao.deleteSession(session)
    }
}
