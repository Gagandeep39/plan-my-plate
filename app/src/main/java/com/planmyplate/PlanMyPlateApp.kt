package com.planmyplate

import android.app.Application
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.*

class PlanMyPlateApp : Application() {
    
    val driveRepository by lazy {
        DriveRepository(this)
    }
    
    val userRepository by lazy {
        UserRepository(this)
    }

    private var _mealRepository: MealRepository? = null
    val mealRepository: MealRepository
        get() = synchronized(this) {
            _mealRepository ?: MealRepository(this).also { _mealRepository = it }
        }

    private var _recipeRepository: RecipeRepository? = null
    val recipeRepository: RecipeRepository
        get() = synchronized(this) {
            _recipeRepository ?: RecipeRepository(this).also { _recipeRepository = it }
        }

    private var _syncLogRepository: SyncLogRepository? = null
    val syncLogRepository: SyncLogRepository
        get() = synchronized(this) {
            _syncLogRepository ?: SyncLogRepository(this).also { _syncLogRepository = it }
        }

    fun resetDatabaseReferences() {
        synchronized(this) {
            AppDatabase.closeAndReset()
            _mealRepository = null
            _recipeRepository = null
            _syncLogRepository = null
        }
    }
}
