package com.planmyplate.data.repository

import android.content.Context
import androidx.work.*
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.MealDao
import com.planmyplate.data.worker.CalendarSyncWorker
import com.planmyplate.data.worker.DriveExportWorker
import com.planmyplate.model.MealSession
import com.planmyplate.model.SessionRecipe
import com.planmyplate.model.SessionWithRecipes
import kotlinx.coroutines.flow.Flow

class MealRepository(
    private val context: Context
) {
    private val mealDao: MealDao
        get() = AppDatabase.getDatabase(context).mealDao()

    companion object {
        const val CALENDAR_SYNC_WORK_TAG = "calendar_sync"
    }

    private fun isCalendarAuthorized(): Boolean {
        val sharedPrefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(UserRepository.KEY_CALENDAR_AUTHORIZED, false)
    }

    private fun isDriveAuthorized(): Boolean {
        val sharedPrefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(UserRepository.KEY_DRIVE_AUTHORIZED, false)
    }

    private fun scheduleDriveExportSync() {
        if (!isDriveAuthorized()) return
        DriveExportWorker.enqueue(context)
    }

    private fun markLocalWrite() {
        context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(UserRepository.KEY_DB_LAST_WRITE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getAllMeals(): Flow<List<SessionWithRecipes>> = mealDao.getAllMeals()

    suspend fun saveMeal(session: MealSession, sessionRecipes: List<SessionRecipe>): Long {
        val sessionId = mealDao.upsertSession(session)

        // Replace existing recipes for this session
        mealDao.deleteRecipesForSession(sessionId)
        
        // Ensure the sessionId is correctly set for all linked recipes
        val toInsert = sessionRecipes.map { it.copy(sessionId = sessionId) }
        
        if (toInsert.isNotEmpty()) {
            mealDao.insertSessionRecipes(toInsert)
        }

        if (isCalendarAuthorized()) {
            scheduleCalendarSync(sessionId)
        }

        markLocalWrite()
        scheduleDriveExportSync()

        return sessionId
    }

    suspend fun deleteMeal(session: MealSession) {
        val eventId = mealDao.getCalendarEventId(session.sessionId)
        
        if (eventId != null && isCalendarAuthorized()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .addTag(CALENDAR_SYNC_WORK_TAG)
                .setInputData(workDataOf(
                    "sessionId" to session.sessionId,
                    "isDeletion" to true,
                    "calendarEventId" to eventId
                ))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "delete_calendar_${session.sessionId}",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
        
        mealDao.deleteRecipesForSession(session.sessionId)
        mealDao.deleteCalendarMapping(session.sessionId)
        mealDao.deleteSession(session)
        markLocalWrite()
        scheduleDriveExportSync()
    }

    private fun scheduleCalendarSync(sessionId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(constraints)
            .addTag(CALENDAR_SYNC_WORK_TAG)
            .setInputData(workDataOf("sessionId" to sessionId))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_calendar_$sessionId",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
