package com.planmyplate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.planmyplate.model.Dish
import com.planmyplate.model.MealCalendarMapping
import com.planmyplate.model.MealSession

@Database(entities = [MealSession::class, Dish::class, MealCalendarMapping::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao

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
