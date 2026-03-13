package com.planmyplate.ui.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SyncCheckState {
    /** Still checking Drive. */
    object Checking : SyncCheckState()

    /** No conflict — proceed to main app. */
    object Clear : SyncCheckState()

    /** Both local and cloud have data but cloud is newer — user must choose. */
    data class Conflict(
        val localTimestamp: Long,
        val cloudTimestamp: Long
    ) : SyncCheckState()

    /** Restoring silently (empty local DB, cloud has data). */
    object Restoring : SyncCheckState()

    /** Restore or resolution completed — app must recreate to pick up new DB. */
    object RestoreComplete : SyncCheckState()

    /** Drive unavailable or sync not enabled — proceed normally. */
    object Skipped : SyncCheckState()
}

class SyncCheckViewModel(
    private val context: Context,
    private val driveRepository: DriveRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SyncCheckState>(SyncCheckState.Checking)
    val state: StateFlow<SyncCheckState> = _state.asStateFlow()
    private var latestCloudTimestamp: Long = 0L

    init {
        viewModelScope.launch { runCheck() }
    }

    private suspend fun runCheck() {
        val prefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val driveAuthorized = prefs.getBoolean(UserRepository.KEY_DRIVE_AUTHORIZED, false)
        val syncEnabled = prefs.getBoolean(UserRepository.KEY_DB_SYNC_ENABLED, false)

        if (!driveAuthorized || !syncEnabled) {
            _state.value = SyncCheckState.Skipped
            return
        }

        val cloudInfo = driveRepository.getCloudBackupInfo()
        if (cloudInfo == null) {
            // No backup on Drive yet — nothing to restore or conflict with
            _state.value = SyncCheckState.Clear
            return
        }
        latestCloudTimestamp = cloudInfo.modifiedTimeMs

        val localMealCount = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).mealDao().getMealCount()
        }

        if (localMealCount == 0) {
            // Empty local DB — silently restore from Drive
            _state.value = SyncCheckState.Restoring
            restoreFromCloud()
            return
        }

        val lastUpload = prefs.getLong(UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP, 0L)
        if (cloudInfo.modifiedTimeMs > lastUpload) {
            // Someone on another device uploaded more recently than our last sync
            val lastWrite = prefs.getLong(UserRepository.KEY_DB_LAST_WRITE_TIMESTAMP, 0L)
            _state.value = SyncCheckState.Conflict(
                localTimestamp = lastWrite,
                cloudTimestamp = cloudInfo.modifiedTimeMs
            )
        } else {
            _state.value = SyncCheckState.Clear
        }
    }

    /** User chose to keep local data. Just record this and continue. */
    fun keepLocal() {
        // Update upload timestamp to match cloud so we stop detecting "conflict" on every restart
        val now = System.currentTimeMillis()
        userRepository.recordUploadTimestamp(
            latestCloudTimestamp.coerceAtLeast(now)
        )
        _state.value = SyncCheckState.Clear
    }

    /** User chose to restore from cloud. Download + replace DB + signal activity to recreate. */
    fun useCloud() {
        _state.value = SyncCheckState.Restoring
        viewModelScope.launch { restoreFromCloud() }
    }

    private suspend fun restoreFromCloud() {
        try {
            val targetFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "plan_my_plate_restore.db")
            }
            val success = driveRepository.downloadDatabaseBackup(targetFile)
            if (!success || !targetFile.exists()) {
                Log.w("SyncCheck", "Download failed, proceeding without restore")
                _state.value = SyncCheckState.Clear
                return
            }

            withContext(Dispatchers.IO) {
                // Close database and clear repository references in Application class
                (context.applicationContext as? PlanMyPlateApp)?.resetDatabaseReferences()
                    ?: AppDatabase.closeAndReset()

                val dbFile = context.getDatabasePath("plan_my_plate_db")
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")

                dbFile.parentFile?.mkdirs()
                targetFile.copyTo(dbFile, overwrite = true)
                // Remove stale WAL/SHM so Room opens cleanly against the restored file
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                // Record the cloud's modified time as our upload timestamp to avoid
                // re-detecting a conflict on the next launch
                val prefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
                val cloudInfo = driveRepository.getCloudBackupInfo()
                prefs.edit()
                    .putLong(
                        UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP,
                        cloudInfo?.modifiedTimeMs ?: System.currentTimeMillis()
                    )
                    .putLong(UserRepository.KEY_DB_LAST_WRITE_TIMESTAMP, System.currentTimeMillis())
                    .apply()

                targetFile.delete()
            }

            _state.value = SyncCheckState.RestoreComplete
        } catch (e: Exception) {
            Log.e("SyncCheck", "Restore failed", e)
            _state.value = SyncCheckState.Clear
        }
    }

    fun formatTimestamp(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ms))
}

class SyncCheckViewModelFactory(
    private val context: Context,
    private val driveRepository: DriveRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SyncCheckViewModel(context, driveRepository, userRepository) as T
    }
}
