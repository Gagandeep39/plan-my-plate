package com.planmyplate.data.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.CalendarRepository
import com.planmyplate.model.MealCalendarMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private fun isCalendarAuthorized(): Boolean {
        val sharedPrefs = applicationContext.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("calendar_authorized", false)
    }

    override suspend fun doWork(): Result {
        if (!isCalendarAuthorized()) {
            return Result.success()
        }

        val sessionId = inputData.getLong("sessionId", -1L)
        if (sessionId == -1L) return Result.failure()

        val isDeletion = inputData.getBoolean("isDeletion", false)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = CalendarRepository(applicationContext)
        val mealDao = database.mealDao()

        return try {
            val toastMessage = if (isDeletion) {
                val eventId = inputData.getString("calendarEventId")
                eventId?.let { repository.deleteEvent(it) }
                "Meal removed from Google Calendar"
            } else {
                val mealWithDishes = mealDao.getMealWithDishes(sessionId) ?: return Result.failure()
                val session = mealWithDishes.session
                val dishNames = mealWithDishes.dishes.map { it.dishName }
                
                var calendarEventId = mealDao.getCalendarEventId(sessionId)
                
                if (calendarEventId == null) {
                    calendarEventId = repository.findExistingEventId(sessionId)
                    if (calendarEventId != null) {
                        mealDao.insertCalendarMapping(MealCalendarMapping(sessionId, calendarEventId))
                    }
                }

                if (calendarEventId == null) {
                    val newEventId = repository.createEvent(session, dishNames)
                    if (newEventId != null) {
                        mealDao.insertCalendarMapping(MealCalendarMapping(sessionId, newEventId))
                    } else {
                        return Result.retry()
                    }
                } else {
                    repository.updateEvent(calendarEventId, session, dishNames)
                }
                "Google Calendar synchronized"
            }

            // Show Toast on the UI thread
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
            }

            Result.success()
        } catch (e: UserRecoverableAuthIOException) {
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
