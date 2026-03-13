package com.planmyplate.data.worker

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.data.repository.UserRepository
import com.planmyplate.model.SyncLog
import java.io.File

class DriveDbSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val DRIVE_DB_SYNC_WORK_TAG = "drive_db_sync"
        private const val UNIQUE_WORK_NAME = "drive_db_backup"
        private const val KEY_FORCE = "force"

        /** Minimum time between consecutive uploads. */
        const val COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveDbSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(DRIVE_DB_SYNC_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueForced(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveDbSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf(KEY_FORCE to true))
                .addTag(DRIVE_DB_SYNC_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val app = applicationContext as PlanMyPlateApp
        val database = AppDatabase.getDatabase(applicationContext)
        val syncLogRepo = app.syncLogRepository
        val driveRepo = app.driveRepository

        if (!prefs.getBoolean(UserRepository.KEY_DRIVE_AUTHORIZED, false) ||
            !prefs.getBoolean(UserRepository.KEY_DB_SYNC_ENABLED, false)
        ) {
            return Result.success()
        }

        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced) {
            val lastSync = prefs.getLong(UserRepository.KEY_DB_LAST_SYNC_TIMESTAMP, 0L)
            val elapsed = System.currentTimeMillis() - lastSync
            if (elapsed < COOLDOWN_MS) return Result.success()
        }

        return try {
            // --- SAFETY CHECK ---
            // Before uploading, check if another device has uploaded something newer
            val cloudInfo = driveRepo.getCloudBackupInfo()
            if (cloudInfo != null) {
                val lastUpload = prefs.getLong(UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP, 0L)
                if (cloudInfo.modifiedTimeMs > lastUpload) {
                    // Conflict detected: Cloud is newer than our last successful interaction.
                    // Stop the background sync to avoid overwriting. User must resolve via UI.
                    syncLogRepo.log(
                        service = SyncLog.SERVICE_DRIVE,
                        action = "Backup database",
                        status = SyncLog.STATUS_SKIPPED,
                        source = SyncLog.SOURCE_QUEUE,
                        message = "Conflict detected: Newer data exists in the cloud. Open the app to resolve."
                    )
                    return Result.success()
                }
            }

            val dbFile = applicationContext.getDatabasePath(AppDatabase.DB_NAME)
            val cacheFile = File(applicationContext.cacheDir, AppDatabase.BACKUP_FILE_NAME)
            
            if (cacheFile.exists()) cacheFile.delete()

            var backupReady = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    database.openHelper.writableDatabase.execSQL("VACUUM INTO '${cacheFile.absolutePath}'")
                    backupReady = cacheFile.exists() && cacheFile.length() > 0
                } catch (e: Exception) { }
            }

            if (!backupReady) {
                try {
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                        val busy = if (cursor.moveToFirst()) cursor.getInt(0) else 1
                        if (busy == 1) {
                            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
                        }
                    }
                } catch (e: Exception) { }
                
                if (dbFile.exists()) {
                    dbFile.copyTo(cacheFile, overwrite = true)
                    backupReady = cacheFile.exists() && cacheFile.length() > 0
                }
            }

            if (!backupReady) throw Exception("Failed to create backup file")

            val mealCount = database.mealDao().getMealCount()
            if (mealCount == 0) {
                 syncLogRepo.log(
                    service = SyncLog.SERVICE_DRIVE,
                    action = "Backup database",
                    status = SyncLog.STATUS_SKIPPED,
                    source = SyncLog.SOURCE_QUEUE,
                    message = "Skipped: local database is empty"
                )
                return Result.success()
            }

            val success = driveRepo.uploadDatabaseBackup()

            if (success) {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong(UserRepository.KEY_DB_LAST_SYNC_TIMESTAMP, now)
                    .putLong(UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP, now)
                    .apply()
                Result.success()
            } else {
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            syncLogRepo.log(
                service = SyncLog.SERVICE_DRIVE,
                action = "Backup database",
                status = SyncLog.STATUS_FAILURE,
                source = SyncLog.SOURCE_QUEUE,
                message = e.message ?: "Unexpected error during backup"
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
