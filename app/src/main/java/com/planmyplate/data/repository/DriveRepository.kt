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
import com.planmyplate.util.AuthAccountResolver
import java.io.FileOutputStream
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

    companion object {
        private const val FOLDER_NAME = "com.planmyplate"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val JSON_BACKUP_NAME = "running_plan.json"
        private val DB_BACKUP_NAME = AppDatabase.BACKUP_FILE_NAME
    }

    private suspend fun insertSyncLog(
        action: String,
        status: String,
        source: String,
        message: String,
        sessionId: Long? = null
    ) {
        SyncLogRepository(context).log(
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
        return try {
            val account = AuthAccountResolver.resolveGoogleAccount(context) ?: return null

            val credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account
            }

            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Plan My Plate").build()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets the ID of the app's dedicated folder, creating it if it doesn't exist.
     */
    private fun getOrCreateFolderId(service: Drive): String? {
        return try {
            val query = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
            val files = service.files().list().setQ(query).setFields("files(id)").execute()
            val existingId = files.files?.firstOrNull()?.id
            if (existingId != null) return existingId

            val metadata = File().apply {
                name = FOLDER_NAME
                mimeType = FOLDER_MIME_TYPE
            }
            service.files().create(metadata).setFields("id").execute().id
        } catch (e: Exception) {
            null
        }
    }

    private fun findExistingJsonFileId(service: Drive, folderId: String): String? {
        val query = "name = '$JSON_BACKUP_NAME' and '$folderId' in parents and trashed = false"
        val files = service.files().list().setQ(query).setFields("files(id)").execute()
        return files.files?.firstOrNull()?.id
    }

    private fun ensureJsonFileExists(service: Drive): String? {
        val folderId = getOrCreateFolderId(service) ?: return null
        val existingId = findExistingJsonFileId(service, folderId)
        if (existingId != null) return existingId

        val metadata = File().apply {
            name = JSON_BACKUP_NAME
            mimeType = "application/json"
            parents = listOf(folderId)
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
        val service = getDriveService()
        if (service == null) {
            insertSyncLog(
                action = "Prepare shareable link",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = "Drive account is not connected"
            )
            return@withContext null
        }
        
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
        val service = getDriveService()
        if (service == null) {
            insertSyncLog(
                action = "Export JSON snapshot",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = "Drive account is not connected"
            )
            return@withContext null
        }

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

    data class CloudBackupInfo(val fileId: String, val modifiedTimeMs: Long, val sizeBytes: Long)

    /**
     * Fetches the Drive backup file's id, modifiedTime, and size without downloading content.
     * Returns null if no backup file exists or Drive is unavailable.
     */
    suspend fun getCloudBackupInfo(): CloudBackupInfo? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null
        try {
            val folderId = getOrCreateFolderId(service) ?: return@withContext null
            val query = "name = '$DB_BACKUP_NAME' and '$folderId' in parents and trashed = false"
            val file = service.files().list()
                .setQ(query)
                .setFields("files(id, modifiedTime, size)")
                .execute()
                .files
                ?.firstOrNull() ?: return@withContext null

            val modifiedMs = file.modifiedTime?.value ?: return@withContext null
            val sizeBytes = file.getSize() ?: 0L
            CloudBackupInfo(fileId = file.id, modifiedTimeMs = modifiedMs, sizeBytes = sizeBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the Drive backup file to [targetFile].
     * Returns true on success.
     */
    suspend fun downloadDatabaseBackup(targetFile: java.io.File, source: String = SyncLog.SOURCE_DIRECT): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService()
        if (service == null) {
            insertSyncLog(
                action = "Restore database",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = "Drive account is not connected"
            )
            return@withContext false
        }
        try {
            val folderId = getOrCreateFolderId(service) ?: return@withContext false
            val query = "name = '$DB_BACKUP_NAME' and '$folderId' in parents and trashed = false"
            val fileId = service.files().list()
                .setQ(query)
                .setFields("files(id)")
                .execute()
                .files
                ?.firstOrNull()
                ?.id ?: return@withContext false

            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { out ->
                service.files().get(fileId).executeMediaAndDownloadTo(out)
            }

            val sizeKb = targetFile.length() / 1024
            insertSyncLog(
                action = "Restore database",
                status = SyncLog.STATUS_SUCCESS,
                source = source,
                message = "Database restored from Drive (${sizeKb}KB)"
            )
            true
        } catch (e: Exception) {
            insertSyncLog(
                action = "Restore database",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = e.message ?: "Download failed"
            )
            false
        }
    }

    /**
     * Uploads the pre-copied SQLite DB file (from cache) to Google Drive.
     * The worker is responsible for WAL checkpoint + file copy before calling this.
     */
    suspend fun uploadDatabaseBackup(source: String = SyncLog.SOURCE_QUEUE): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService()
        if (service == null) {
            insertSyncLog(
                action = "Backup database",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = "Drive account is not connected"
            )
            return@withContext false
        }

        try {
            val folderId = getOrCreateFolderId(service) ?: return@withContext false
            val cacheFile = java.io.File(context.cacheDir, DB_BACKUP_NAME)
            if (!cacheFile.exists()) {
                insertSyncLog(
                    action = "Backup database",
                    status = SyncLog.STATUS_FAILURE,
                    source = source,
                    message = "Backup file not found in cache"
                )
                return@withContext false
            }

            val query = "name = '$DB_BACKUP_NAME' and '$folderId' in parents and trashed = false"
            val existingId = service.files().list()
                .setQ(query)
                .setFields("files(id)")
                .execute()
                .files
                ?.firstOrNull()
                ?.id

            val content = FileContent("application/octet-stream", cacheFile)

            if (existingId != null) {
                service.files().update(existingId, null, content).setFields("id").execute()
            } else {
                val metadata = File().apply {
                    name = DB_BACKUP_NAME
                    mimeType = "application/octet-stream"
                    parents = listOf(folderId)
                }
                service.files().create(metadata, content).setFields("id").execute()
            }

            val sizeKb = cacheFile.length() / 1024
            insertSyncLog(
                action = "Backup database",
                status = SyncLog.STATUS_SUCCESS,
                source = source,
                message = "Database backed up successfully (${sizeKb}KB)"
            )
            true
        } catch (e: Exception) {
            insertSyncLog(
                action = "Backup database",
                status = SyncLog.STATUS_FAILURE,
                source = source,
                message = e.message ?: "Unknown Drive error"
            )
            false
        }
    }
}
