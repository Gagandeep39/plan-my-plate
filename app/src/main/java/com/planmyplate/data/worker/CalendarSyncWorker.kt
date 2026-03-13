package com.planmyplate.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.model.SyncLog
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.planmyplate.util.AuthAccountResolver
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

        if (sessionId == -1L && !isDeletion) return@withContext Result.failure()

        try {
            val account = AuthAccountResolver.resolveGoogleAccount(applicationContext)
            if (account == null) {
                syncLogRepository.log(
                    SyncLog.SERVICE_CALENDAR,
                    "Sync Meal",
                    SyncLog.STATUS_FAILURE,
                    SyncLog.SOURCE_QUEUE,
                    "Google account not connected"
                )
                return@withContext Result.failure()
            }

            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext, Collections.singleton(CalendarScopes.CALENDAR)
            ).apply {
                selectedAccount = account
            }

            val service = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Plan My Plate").build()

            if (isDeletion) {
                val calendarEventId = inputData.getString("calendarEventId")
                if (calendarEventId != null) {
                    service.events().delete("primary", calendarEventId).execute()
                }
                return@withContext Result.success()
            }

            val mealWithDishes = database.mealDao().getMealWithDishes(sessionId) ?: return@withContext Result.failure()
            val existingEventId = database.mealDao().getCalendarEventId(sessionId)

            val event = Event().apply {
                summary = "Meal: " + mealWithDishes.dishes.joinToString(", ") { it.dishName }
                description = mealWithDishes.session.notes ?: ""
                start = EventDateTime().apply {
                    dateTime = com.google.api.client.util.DateTime(mealWithDishes.session.scheduledTimestamp)
                }
                end = EventDateTime().apply {
                    dateTime = com.google.api.client.util.DateTime(mealWithDishes.session.scheduledTimestamp + 3600000) // 1 hour
                }
            }

            if (existingEventId != null) {
                service.events().update("primary", existingEventId, event).execute()
            } else {
                val createdEvent = service.events().insert("primary", event).execute()
                database.mealDao().insertCalendarMapping(
                    com.planmyplate.model.MealCalendarMapping(sessionId, createdEvent.id)
                )
            }

            syncLogRepository.log(
                SyncLog.SERVICE_CALENDAR,
                "Sync Meal",
                SyncLog.STATUS_SUCCESS,
                SyncLog.SOURCE_QUEUE,
                "Synced meal to calendar",
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
