package com.planmyplate.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

        /** Minimum time between consecutive uploads. Rapid data changes are debounced naturally. */
        const val COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes

        /** Normal enqueue — honours cooldown and uses KEEP policy. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveDbSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(DRIVE_DB_SYNC_WORK_TAG)
                .build()

            // KEEP: if a job is already queued or running, don't replace it.
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** Force enqueue — bypasses cooldown, replaces any queued job. */
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
        val database = AppDatabase.getDatabase(applicationContext)
        val syncLogRepo = SyncLogRepository(database.syncLogDao())

        // Guard: only run when Drive is authorised and the user has the toggle on
        if (!prefs.getBoolean(UserRepository.KEY_DRIVE_AUTHORIZED, false) ||
            !prefs.getBoolean(UserRepository.KEY_DB_SYNC_ENABLED, false)
        ) {
            syncLogRepo.log(
                service = SyncLog.SERVICE_DRIVE,
                action = "Backup database",
                status = SyncLog.STATUS_SKIPPED,
                source = SyncLog.SOURCE_QUEUE,
                message = "Skipped: Drive backup is disabled or Drive is not connected"
            )
            return Result.success()
        }

        // Cooldown check — skip if too soon, unless this was a forced manual sync
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced) {
            val lastSync = prefs.getLong(UserRepository.KEY_DB_LAST_SYNC_TIMESTAMP, 0L)
            val elapsed = System.currentTimeMillis() - lastSync
            if (elapsed < COOLDOWN_MS) {
                val remainingMin = (COOLDOWN_MS - elapsed) / 60_000
                syncLogRepo.log(
                    service = SyncLog.SERVICE_DRIVE,
                    action = "Backup database",
                    status = SyncLog.STATUS_SKIPPED,
                    source = SyncLog.SOURCE_QUEUE,
                    message = "Cooldown active — next backup available in ${remainingMin}m"
                )
                return Result.success()
            }
        }

        return try {
            // Flush any pending WAL writes so the main DB file is complete
            database.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).close()

            // Copy the DB file to cache before uploading
            val dbFile = applicationContext.getDatabasePath("plan_my_plate_db")
            val cacheFile = File(applicationContext.cacheDir, "plan_my_plate_backup.db")
            dbFile.copyTo(cacheFile, overwrite = true)

            val success = DriveRepository(applicationContext).uploadDatabaseBackup()

            if (success) {
                prefs.edit()
                    .putLong(UserRepository.KEY_DB_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
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
                message = e.message ?: "Unexpected error during database backup"
            )
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
