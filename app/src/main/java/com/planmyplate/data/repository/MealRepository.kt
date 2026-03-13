package com.planmyplate.data.repository

import com.planmyplate.data.MealDao
import com.planmyplate.model.Dish
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.flow.Flow

class MealRepository(private val mealDao: MealDao) {
    fun getAllMeals(): Flow<List<MealWithDishes>> = mealDao.getAllMeals()

    suspend fun saveMeal(session: MealSession, dishes: List<String>): Long {
        val sessionId = mealDao.insertSession(session)
        
        // Simple syncing logic: insert current dishes
        // In a more robust app, we might clear old dishes if this is an update
        val dishesToInsert = dishes.map { dishName ->
            Dish(parentSessionId = sessionId, dishName = dishName)
        }
        if (dishesToInsert.isNotEmpty()) {
            mealDao.insertDishes(dishesToInsert)
        }
        return sessionId
    }

    suspend fun deleteMeal(session: MealSession) = mealDao.deleteSession(session)
}
