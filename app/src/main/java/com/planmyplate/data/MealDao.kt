package com.planmyplate.data

import androidx.room.*
import com.planmyplate.model.Dish
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meal_sessions ORDER BY scheduledTimestamp DESC")
    fun getAllMeals(): Flow<List<MealWithDishes>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MealSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDishes(dishes: List<Dish>)

    @Delete
    suspend fun deleteSession(session: MealSession)
}
