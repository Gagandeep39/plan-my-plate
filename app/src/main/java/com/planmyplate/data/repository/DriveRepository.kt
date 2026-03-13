package com.planmyplate.data.repository

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import com.planmyplate.data.AppDatabase
import com.planmyplate.model.MealWithDishes
import com.planmyplate.model.SyncLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DriveRepository(private val context: Context) {

    private suspend fun insertSyncLog(
        action: String,
        status: String,
        source: String,
        message: String,
        sessionId: Long? = null
    ) {
        SyncLogRepository(AppDatabase.getDatabase(context).syncLogDao()).log(
            service = SyncLog.SERVICE_DRIVE,
            action = action,
            status = status,
            source = source,
            message = message,
            sessionId = sessionId
        )
    }

    private fun buildDirectJsonUrl(fileId: String): String {
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    private fun getDriveService(): Drive? {
        val sharedPrefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString(UserRepository.KEY_USER_EMAIL, null) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccountName = userEmail
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Plan My Plate").build()
    }

    private fun findExistingJsonFileId(service: Drive): String? {
        val query = "name = 'running_plan.json' and trashed = false"
        val files = service.files().list().setQ(query).setFields("files(id)").execute()
        return files.files?.firstOrNull()?.id
    }

    private fun ensureJsonFileExists(service: Drive): String? {
        val existingId = findExistingJsonFileId(service)
        if (existingId != null) return existingId

        val metadata = File().apply {
            name = "running_plan.json"
            mimeType = "application/json"
        }
        val emptyFile = java.io.File(context.cacheDir, "temp.json").apply { writeText("[]") }
        val content = FileContent("application/json", emptyFile)

        return service.files().create(metadata, content)
            .setFields("id")
            .execute()
            .id
    }

    private fun ensurePublicReadPermission(service: Drive, fileId: String) {
        try {
            val permission = Permission().apply {
                type = "anyone"
                role = "reader"
            }
            service.permissions().create(fileId, permission).execute()
        } catch (_: Exception) {
            // Ignore if permission already exists or Drive rejects duplicate permission creation.
        }
    }

    private fun formatIsoUtc(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
    }

    private fun formatLocalDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatLocalTime(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun buildMealsJson(meals: List<MealWithDishes>): String {
        val jsonArray = JSONArray()
        meals.forEach { meal ->
            val dishNames = meal.dishes.map { it.dishName.trim() }.filter { it.isNotBlank() }
            val dishArray = JSONArray()
            dishNames.forEach { dishArray.put(it) }

            val jsonObject = JSONObject().apply {
                put("sessionId", meal.session.sessionId)
                put("mealType", meal.session.mealType)
                put("mealName", dishNames.joinToString(", "))
                put("mealTime", formatIsoUtc(meal.session.scheduledTimestamp))
                put("scheduledDate", formatLocalDate(meal.session.scheduledTimestamp))
                put("displayTime", formatLocalTime(meal.session.scheduledTimestamp))
                put("dishNames", dishArray)
                put("notes", meal.session.notes ?: JSONObject.NULL)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString(2)
    }

    suspend fun createOrGetSharableJsonFile(source: String = SyncLog.SOURCE_DIRECT): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null
        
        try {
            val fileId = ensureJsonFileExists(service)

            if (fileId != null) {
                ensurePublicReadPermission(service, fileId)
                val link = buildDirectJsonUrl(fileId)
                insertSyncLog(
                    action = "Prepare shareable link",
                    status = SyncLog.STATUS_SUCCESS,
                    source = source,
                    message = "Drive JSON link is ready"
                )
                link
            } else {
                insertSyncLog(
                    action = "Prepare shareable link",
                    status = SyncLog.STATUS_FAILURE,
                    source = source,
                    message = "Drive JSON file could not be created"
                )
                null
            }
        } catch (e: Exception) {
            insertSyncLog(
                action = "Prepare shareable link",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = e.message ?: "Unknown Drive error"
            )
            null
        }
    }

    suspend fun replaceSharableJsonFileContent(
        meals: List<MealWithDishes>,
        source: String = SyncLog.SOURCE_QUEUE
    ): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null

        try {
            val fileId = ensureJsonFileExists(service) ?: return@withContext null
            ensurePublicReadPermission(service, fileId)

            val jsonFile = java.io.File(context.cacheDir, "running_plan_export.json").apply {
                writeText(buildMealsJson(meals))
            }
            val content = FileContent("application/json", jsonFile)

            service.files().update(fileId, null, content)
                .setFields("id")
                .execute()

            val link = buildDirectJsonUrl(fileId)
            insertSyncLog(
                action = "Export JSON snapshot",
                status = SyncLog.STATUS_SUCCESS,
                source = source,
                message = "Updated Drive JSON with ${meals.size} meals"
            )
            link
        } catch (e: Exception) {
            insertSyncLog(
                action = "Export JSON snapshot",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = e.message ?: "Unknown Drive error"
            )
            null
        }
    }
}
