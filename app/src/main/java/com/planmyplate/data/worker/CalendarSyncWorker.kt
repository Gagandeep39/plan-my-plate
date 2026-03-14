package com.planmyplate.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.model.SyncLog
import com.planmyplate.data.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class CalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext)
        val syncLogRepository = SyncLogRepository(applicationContext)
        val sessionId = inputData.getLong("sessionId", -1L)
        val isDeletion = inputData.getBoolean("isDeletion", false)
        val calendarEventId = inputData.getString("calendarEventId")
        val calendarRepository = CalendarRepository(applicationContext)

        if (sessionId == -1L && !isDeletion) return@withContext Result.failure()

        try {
            if (isDeletion) {
                if (calendarEventId != null) {
                    calendarRepository.deleteEvent(calendarEventId)
                }
                return@withContext Result.success()
            }

            val mealWithDishes = database.mealDao().getMealWithDishes(sessionId) ?: return@withContext Result.failure()
            val existingEventId = database.mealDao().getCalendarEventId(sessionId)

            var message = ""
            if (existingEventId != null) {
                calendarRepository.updateEvent(existingEventId, mealWithDishes.session, mealWithDishes.dishes.map { it.dishName })
                message = "Updated meal in calendar with ID $existingEventId"
            } else {
                val createdEventId = calendarRepository.createEvent(mealWithDishes.session, mealWithDishes.dishes.map { it.dishName })
                if (createdEventId != null) {
                    database.mealDao().insertCalendarMapping(
                        com.planmyplate.model.MealCalendarMapping(sessionId, createdEventId)
                    )
                }
                message = "Created meal in calendar with ID $createdEventId"
            }

            syncLogRepository.log(
                SyncLog.SERVICE_CALENDAR,
                "Sync Meal",
                SyncLog.STATUS_SUCCESS,
                SyncLog.SOURCE_QUEUE,
                message,
                sessionId
            )
            Result.success()
        } catch (e: Exception) {
            syncLogRepository.log(
                SyncLog.SERVICE_CALENDAR,
                "Sync Meal",
                SyncLog.STATUS_FAILURE,
                SyncLog.SOURCE_QUEUE,
                e.message ?: "Unknown error",
                if (sessionId != -1L) sessionId else null
            )
            Result.retry()
        }
    }
}
