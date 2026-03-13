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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class DriveRepository(private val context: Context) {

    private fun buildDirectJsonUrl(fileId: String): String {
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    private fun getDriveService(): Drive? {
        val sharedPrefs = context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("user_email", null) ?: return null

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

    suspend fun createOrGetSharableJsonFile(): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null
        
        try {
            // 1. Check if file already exists
            val query = "name = 'running_plan.json' and trashed = false"
            val files = service.files().list().setQ(query).setFields("files(id)").execute()
            var fileId = files.files?.firstOrNull()?.id

            // 2. Create if not exists
            if (fileId == null) {
                val metadata = File().apply {
                    name = "running_plan.json"
                    mimeType = "application/json"
                }
                // Initial empty content
                val emptyFile = java.io.File(context.cacheDir, "temp.json").apply { writeText("{}") }
                val content = FileContent("application/json", emptyFile)
                
                val createdFile = service.files().create(metadata, content)
                    .setFields("id")
                    .execute()
                fileId = createdFile.id
            }

            // 3. Make it publicly sharable
            if (fileId != null) {
                val permission = Permission().apply {
                    type = "anyone"
                    role = "reader"
                }
                service.permissions().create(fileId, permission).execute()
                // Return a direct download/content URL instead of Drive's file-view wrapper.
                buildDirectJsonUrl(fileId)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
