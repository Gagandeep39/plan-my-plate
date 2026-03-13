package com.planmyplate.data.repository

import android.content.Context
import androidx.work.*
import com.planmyplate.data.MealDao
import com.planmyplate.data.worker.CalendarSyncWorker
import com.planmyplate.data.worker.DriveExportWorker
import com.planmyplate.model.Dish
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.flow.Flow

class MealRepository(
    private val context: Context,
    private val mealDao: MealDao
) {
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

    fun getAllMeals(): Flow<List<MealWithDishes>> = mealDao.getAllMeals()

    suspend fun saveMeal(session: MealSession, dishes: List<String>): Long {
        val sessionId = mealDao.insertSession(session)

        // Replace existing dishes for this session
        mealDao.deleteDishesForSession(sessionId)
        val dishesToInsert = dishes.map { dishName ->
            Dish(parentSessionId = sessionId, dishName = dishName)
        }
        if (dishesToInsert.isNotEmpty()) {
            mealDao.insertDishes(dishesToInsert)
        }

        if (isCalendarAuthorized()) {
            scheduleCalendarSync(sessionId)
        }

        scheduleDriveExportSync()

        return sessionId
    }

    suspend fun deleteMeal(session: MealSession) {
        val eventId = mealDao.getCalendarEventId(session.sessionId)
        
        // Schedule deletion in calendar
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
        
        mealDao.deleteDishesForSession(session.sessionId)
        mealDao.deleteCalendarMapping(session.sessionId)
        mealDao.deleteSession(session)
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
