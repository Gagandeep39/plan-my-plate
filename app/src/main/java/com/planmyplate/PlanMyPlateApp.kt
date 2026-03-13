package com.planmyplate

import android.app.Application
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.MealRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.data.repository.UserRepository

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

    private var _syncLogRepository: SyncLogRepository? = null
    val syncLogRepository: SyncLogRepository
        get() = synchronized(this) {
            _syncLogRepository ?: SyncLogRepository(this).also { _syncLogRepository = it }
        }

    /**
     * Clears cached database instances and repositories that hold references to them.
     * This is necessary after a database restore to ensure the app picks up the new data.
     */
    fun resetDatabaseReferences() {
        synchronized(this) {
            AppDatabase.closeAndReset()
            _mealRepository = null
            _syncLogRepository = null
        }
    }
}
