package com.planmyplate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.planmyplate.model.Dish
import com.planmyplate.model.MealCalendarMapping
import com.planmyplate.model.MealSession
import com.planmyplate.model.SyncLog

@Database(entities = [MealSession::class, Dish::class, MealCalendarMapping::class, SyncLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plan_my_plate_db"
                )

                .fallbackToDestructiveMigration(true) // Simpler for development, handles schema change
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
