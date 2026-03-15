package com.planmyplate.data

import androidx.room.*
import com.planmyplate.model.MealCalendarMapping
import com.planmyplate.model.MealSession
import com.planmyplate.model.SessionRecipe
import com.planmyplate.model.SessionWithRecipes
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal_sessions ORDER BY scheduledTimestamp DESC")
    fun getAllMeals(): Flow<List<SessionWithRecipes>>

    @Transaction
    @Query("SELECT * FROM meal_sessions WHERE scheduledTimestamp BETWEEN :startInclusive AND :endInclusive ORDER BY scheduledTimestamp ASC")
    suspend fun getMealsInRange(startInclusive: Long, endInclusive: Long): List<SessionWithRecipes>

    @Transaction
    @Query("SELECT * FROM meal_sessions WHERE sessionId = :sessionId")
    suspend fun getMealWithRecipes(sessionId: Long): SessionWithRecipes?

    @Upsert
    suspend fun upsertSession(session: MealSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionRecipes(recipes: List<SessionRecipe>)

    @Delete
    suspend fun deleteSession(session: MealSession)

    @Upsert
    suspend fun upsertCalendarMapping(mapping: MealCalendarMapping)

    @Query("SELECT calendarEventId FROM meal_calendar_mappings WHERE sessionId = :sessionId")
    suspend fun getCalendarEventId(sessionId: Long): String?

    @Query("DELETE FROM meal_calendar_mappings WHERE sessionId = :sessionId")
    suspend fun deleteCalendarMapping(sessionId: Long)

    @Query("DELETE FROM session_recipes WHERE sessionId = :sessionId")
    suspend fun deleteRecipesForSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM meal_sessions")
    suspend fun getMealCount(): Int
}
