package com.planmyplate

import android.app.Application
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.MealRepository
import com.planmyplate.data.repository.UserRepository

class PlanMyPlateApp : Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }

    val driveRepository by lazy {
        DriveRepository(this)
    }
    
    val mealRepository by lazy { 
        MealRepository(this, database.mealDao()) 
    }
    
    val userRepository by lazy {
        UserRepository(this)
    }
}
