package com.planmyplate.data.repository

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

    private fun getCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(CalendarScopes.CALENDAR)
        ).apply {
            selectedAccount = account.account
        }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Plan My Plate").build()
    }

    suspend fun findExistingEventId(sessionId: Long): String? = withContext(Dispatchers.IO) {
        val service = getCalendarService() ?: return@withContext null
        try {
            // Search for events with our custom property
            val events = service.events().list("primary")
                .setPrivateExtendedProperty(listOf("planMyPlateSessionId=$sessionId"))
                .execute()
            events.items.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createEvent(mealSession: MealSession, dishNames: List<String>): String? = withContext(Dispatchers.IO) {
        val service = getCalendarService() ?: return@withContext null

        val event = Event().apply {
            summary = mealSession.mealType.lowercase().replaceFirstChar { it.uppercase() } + ": " + dishNames.joinToString(", ")
            description = (mealSession.notes?.let { "\nNotes: $it" } ?: "")
            
            val start = DateTime(mealSession.scheduledTimestamp)
            val end = DateTime(mealSession.scheduledTimestamp + 1800000) // 1 hour duration
            
            setStart(EventDateTime().setDateTime(start))
            setEnd(EventDateTime().setDateTime(end))

            // Add metadata to prevent duplicates
            extendedProperties = Event.ExtendedProperties().apply {
                private = mapOf("planMyPlateSessionId" to mealSession.sessionId.toString())
            }
        }

        try {
            val createdEvent = service.events().insert("primary", event).execute()
            createdEvent.id
        } catch (e: UserRecoverableAuthIOException) {
            Log.e("CalendarRepository", "Auth consent required", e)
            throw e
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error creating event", e)
            null
        }
    }

    suspend fun updateEvent(calendarEventId: String, mealSession: MealSession, dishNames: List<String>) = withContext(Dispatchers.IO) {
        val service = getCalendarService() ?: return@withContext
        
        try {
            val event = service.events().get("primary", calendarEventId).execute()
            event.apply {
                summary = mealSession.mealType.lowercase().replaceFirstChar { it.uppercase() } + ": " + dishNames.joinToString(", ")
                description = (mealSession.notes?.let { "\nNotes: $it" } ?: "")
                
                val start = DateTime(mealSession.scheduledTimestamp)
                val end = DateTime(mealSession.scheduledTimestamp + 1800000)
                
                setStart(EventDateTime().setDateTime(start))
                setEnd(EventDateTime().setDateTime(end))
            }

            service.events().update("primary", calendarEventId, event).execute()
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error updating event", e)
        }
    }

    suspend fun deleteEvent(calendarEventId: String) = withContext(Dispatchers.IO) {
        val service = getCalendarService() ?: return@withContext
        try {
            service.events().delete("primary", calendarEventId).execute()
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Error deleting event", e)
        }
    }
}
