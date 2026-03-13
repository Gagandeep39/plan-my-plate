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

                .fallbackToDestructiveMigration(true) // Simpler for development, handles schema change
                .build()
                INSTANCE = instance
                instance
            }
        }

        /** Closes the current DB instance so it can be replaced with a restored file. */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
