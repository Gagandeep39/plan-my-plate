package com.planmyplate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.planmyplate.model.*

@Database(
    entities = [
        MealSession::class, 
        SessionRecipe::class, 
        Recipe::class, 
        Ingredient::class,
        MealCalendarMapping::class, 
        SyncLog::class
    ], 
    version = 4, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun syncLogDao(): SyncLogDao

    companion object {
        const val DB_NAME = "plan_my_plate_db"
        const val BACKUP_FILE_NAME = "data.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
