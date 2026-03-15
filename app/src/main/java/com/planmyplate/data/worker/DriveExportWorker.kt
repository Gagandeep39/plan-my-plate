package com.planmyplate.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.data.repository.UserRepository
import com.planmyplate.model.SyncLog
import java.util.Calendar

class DriveExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val DRIVE_EXPORT_WORK_TAG = "drive_export"
        private const val DRIVE_EXPORT_UNIQUE_WORK = "drive_export_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DriveExportWorker>()
                .setConstraints(constraints)
                .addTag(DRIVE_EXPORT_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                DRIVE_EXPORT_UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private fun isDriveAuthorized(): Boolean {
        val sharedPrefs = applicationContext.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(UserRepository.KEY_DRIVE_AUTHORIZED, false)
    }

    override suspend fun doWork(): Result {
        val syncLogRepository = SyncLogRepository(applicationContext)

        if (!isDriveAuthorized()) {
            syncLogRepository.log(
                service = SyncLog.SERVICE_DRIVE,
                action = "Export JSON snapshot",
                status = SyncLog.STATUS_SKIPPED,
                source = SyncLog.SOURCE_QUEUE,
                message = "Skipped because Drive is disconnected"
            )
            return Result.success()
        }

        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val mealDao = database.mealDao()
            val driveRepository = DriveRepository(applicationContext)

            val calendar = Calendar.getInstance()
            val start = (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -3)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val end = (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 7)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val meals = mealDao.getMealsInRange(start, end)
            val link = driveRepository.replaceSharableJsonFileContent(meals, source = SyncLog.SOURCE_QUEUE)

            if (link != null) {
                applicationContext
                    .getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(UserRepository.KEY_DRIVE_SHARABLE_LINK, link)
                    .apply()
                Result.success()
            } else {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
