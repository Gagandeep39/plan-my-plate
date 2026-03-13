package com.planmyplate.data

import androidx.room.*
import com.planmyplate.model.Dish
import com.planmyplate.model.MealCalendarMapping
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal_sessions ORDER BY scheduledTimestamp DESC")
    fun getAllMeals(): Flow<List<MealWithDishes>>

    @Transaction
    @Query("SELECT * FROM meal_sessions WHERE scheduledTimestamp BETWEEN :startInclusive AND :endInclusive ORDER BY scheduledTimestamp ASC")
    suspend fun getMealsInRange(startInclusive: Long, endInclusive: Long): List<MealWithDishes>

    @Transaction
    @Query("SELECT * FROM meal_sessions WHERE sessionId = :sessionId")
    suspend fun getMealWithDishes(sessionId: Long): MealWithDishes?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MealSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDishes(dishes: List<Dish>)

    @Delete
    suspend fun deleteSession(session: MealSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarMapping(mapping: MealCalendarMapping)

    @Query("SELECT calendarEventId FROM meal_calendar_mappings WHERE sessionId = :sessionId")
    suspend fun getCalendarEventId(sessionId: Long): String?

    @Query("DELETE FROM meal_calendar_mappings WHERE sessionId = :sessionId")
    suspend fun deleteCalendarMapping(sessionId: Long)

    @Query("DELETE FROM dishes WHERE parentSessionId = :sessionId")
    suspend fun deleteDishesForSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM meal_sessions")
    suspend fun getMealCount(): Int
}
