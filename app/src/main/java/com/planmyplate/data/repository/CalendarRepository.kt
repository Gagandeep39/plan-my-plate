package com.planmyplate.data.repository

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.planmyplate.model.MealSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class CalendarRepository(private val context: Context) {

    private fun resolveGoogleAccount(): Account? {
        val sharedPrefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)

        val signedInAccount = GoogleSignIn.getLastSignedInAccount(context)?.account
        if (signedInAccount?.name?.isNotBlank() == true) {
            sharedPrefs.edit().putString(UserRepository.KEY_USER_EMAIL, signedInAccount.name).apply()
            return signedInAccount
        }

        val emailFromPrefs = sharedPrefs
            .getString(UserRepository.KEY_USER_EMAIL, null)
            ?.trim()
            ?.takeIf { it.contains("@") }
            ?: return null

        return try {
            Account(emailFromPrefs, "com.google")
        } catch (_: Exception) {
            null
        }
    }

    private fun getCalendarService(): Calendar? {
        val account = resolveGoogleAccount() ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(CalendarScopes.CALENDAR)
        ).apply {
            selectedAccount = account
        }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Plan My Plate").build()
    }

    private fun prepareEvent(mealSession: MealSession, dishNames: List<String>): Event {
        return Event().apply {
            summary = "${mealSession.mealType.lowercase().replaceFirstChar { it.uppercase() }}: ${dishNames.joinToString(", ")}"
            colorId = "3" // Grape (Purple)
            
            description = buildString {
                // Ensure the description starts immediately on the first line with notes (if any)
                val notes = mealSession.notes
                if (!notes.isNullOrBlank()) {
                    append(notes)
                    append("\n")
                }
                append("---\nCreated by Plan my Plate")
            }
            
            val start = DateTime(mealSession.scheduledTimestamp)
            val end = DateTime(mealSession.scheduledTimestamp + 1800000) // 30 min duration
            
            setStart(EventDateTime().setDateTime(start))
            setEnd(EventDateTime().setDateTime(end))

            extendedProperties = Event.ExtendedProperties().apply {
                private = mapOf("planMyPlateSessionId" to mealSession.sessionId.toString())
            }
        }
    }

    private suspend fun <T> runCalendarTask(action: suspend (Calendar) -> T): T? = withContext(Dispatchers.IO) {
        val service = getCalendarService() ?: return@withContext null
        try {
            action(service)
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Operation failed", e)
            null
        }
    }

    suspend fun findExistingEventId(sessionId: Long): String? = runCalendarTask { service ->
        val events = service.events().list("primary")
            .setPrivateExtendedProperty(listOf("planMyPlateSessionId=$sessionId"))
            .execute()
        events.items?.firstOrNull()?.id
    }

    suspend fun createEvent(mealSession: MealSession, dishNames: List<String>): String? = runCalendarTask { service ->
        val event = prepareEvent(mealSession, dishNames)
        service.events().insert("primary", event).execute().id
    }

    suspend fun updateEvent(calendarEventId: String, mealSession: MealSession, dishNames: List<String>) {
        runCalendarTask { service ->
            val event = service.events().get("primary", calendarEventId).execute()
            val updatedData = prepareEvent(mealSession, dishNames)
            
            event.summary = updatedData.summary
            event.description = updatedData.description
            event.start = updatedData.start
            event.end = updatedData.end
            event.colorId = updatedData.colorId
            
            service.events().update("primary", calendarEventId, event).execute()
        }
    }

    suspend fun deleteEvent(calendarEventId: String) {
        runCalendarTask { service ->
            service.events().delete("primary", calendarEventId).execute()
        }
    }
}
