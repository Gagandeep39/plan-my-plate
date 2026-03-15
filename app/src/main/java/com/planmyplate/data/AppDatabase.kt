package com.planmyplate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.planmyplate.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        MealSession::class, 
        SessionRecipe::class, 
        Recipe::class, 
        Ingredient::class,
        MealCalendarMapping::class, 
        SyncLog::class
    ], 
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun syncLogDao(): SyncLogDao

    /**
     * Generically checks if the database contains any user data.
     * Excludes system tables and sync logs.
     */
    suspend fun isDatabaseEmpty(): Boolean = withContext(Dispatchers.IO) {
        val db = openHelper.readableDatabase
        val tables = mutableListOf<String>()
        
        // 1. Get all table names excluding internal SQLite/Room tables and logs
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name != "android_metadata" && 
                    name != "sqlite_sequence" && 
                    name != "room_master_table" && 
                    name != "sync_logs") {
                    tables.add(name)
                }
            }
        }
        
        // 2. Check if any of those tables have at least one record
        for (table in tables) {
            // Using "LIMIT 1" is much faster than COUNT(*) for checking existence
            db.query("SELECT 1 FROM \"$table\" LIMIT 1").use { cursor ->
                if (cursor.moveToFirst()) return@withContext false
            }
        }
        true
    }

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
