package com.planmyplate.ui.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.data.repository.UserRepository
import com.planmyplate.model.SyncLog
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
    object Checking : SyncCheckState()
    object Clear : SyncCheckState()
    data class Conflict(
        val localTimestamp: Long,
        val cloudTimestamp: Long
    ) : SyncCheckState()
    object Restoring : SyncCheckState()
    object RestoreComplete : SyncCheckState()
    object Skipped : SyncCheckState()
}

class SyncCheckViewModel(
    private val context: Context,
    private val driveRepository: DriveRepository,
    private val userRepository: UserRepository,
    private val syncLogRepository: SyncLogRepository,
    val isManualSync: Boolean = false
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
        val lastWrite = prefs.getLong(UserRepository.KEY_DB_LAST_WRITE_TIMESTAMP, 0L)
        val lastUpload = prefs.getLong(UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP, 0L)
        val hasLocalChanges = lastWrite > lastUpload

        // 1. No cloud backup exists yet
        if (cloudInfo == null) {
            if (isManualSync || hasLocalChanges) userRepository.enqueueDbSyncForced()
            _state.value = SyncCheckState.Clear
            return
        }

        latestCloudTimestamp = cloudInfo.modifiedTimeMs

        // 2. Generic empty local DB check
        val isLocalEmpty = AppDatabase.getDatabase(context).isDatabaseEmpty()
        
        if (isLocalEmpty) {
            _state.value = SyncCheckState.Restoring
            restoreFromCloud()
            return
        }

        // 3. Conflict detection: Cloud is newer than our last successful interaction
        if (cloudInfo.modifiedTimeMs > lastUpload) {
            _state.value = SyncCheckState.Conflict(
                localTimestamp = lastWrite,
                cloudTimestamp = cloudInfo.modifiedTimeMs
            )
        } else {
            // 4. Local is up-to-date or ahead. Backup if manual OR if changes exist.
            if (isManualSync || hasLocalChanges) {
                userRepository.enqueueDbSyncForced()
            }
            _state.value = SyncCheckState.Clear
        }
    }

    fun keepLocal() {
        val now = System.currentTimeMillis()
        userRepository.recordUploadTimestamp(latestCloudTimestamp.coerceAtLeast(now))
        userRepository.enqueueDbSyncForced()
        _state.value = SyncCheckState.Clear
    }

    fun useCloud() {
        _state.value = SyncCheckState.Restoring
        viewModelScope.launch { restoreFromCloud() }
    }

    private suspend fun restoreFromCloud() {
        try {
            val targetFile = withContext(Dispatchers.IO) {
                File(context.cacheDir, "plan_my_plate_restore.db")
            }
            val downloadSuccess = driveRepository.downloadDatabaseBackup(targetFile)
            if (!downloadSuccess || !targetFile.exists()) {
                _state.value = SyncCheckState.Clear
                return
            }

            withContext(Dispatchers.IO) {
                // 1. Close existing DB and reset app-level repository references
                val app = context.applicationContext as? PlanMyPlateApp
                app?.resetDatabaseReferences() ?: AppDatabase.closeAndReset()

                // 2. Perform file swap
                val dbFile = context.getDatabasePath("plan_my_plate_db")
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")

                dbFile.parentFile?.mkdirs()
                targetFile.copyTo(dbFile, overwrite = true)
                
                // Important: Delete journal files of the OLD database to avoid corruption
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                // 3. Update Sync Timestamps
                val prefs = context.getSharedPreferences(UserRepository.PREFS_NAME, Context.MODE_PRIVATE)
                val cloudInfo = driveRepository.getCloudBackupInfo()
                val cloudTs = cloudInfo?.modifiedTimeMs ?: System.currentTimeMillis()
                
                prefs.edit()
                    .putLong(UserRepository.KEY_DB_LAST_UPLOAD_TIMESTAMP, cloudTs)
                    .putLong(UserRepository.KEY_DB_LAST_WRITE_TIMESTAMP, cloudTs)
                    .commit() // Use commit() for immediate write

                // 4. Create log entry in the RESTORED database
                // Open a fresh connection to the newly swapped file
                val db = AppDatabase.getDatabase(context)
                db.syncLogDao().insertSyncLog(
                    SyncLog(
                        service = SyncLog.SERVICE_DRIVE,
                        action = "Restore Database",
                        status = SyncLog.STATUS_SUCCESS,
                        source = if (isManualSync) SyncLog.SOURCE_DIRECT else SyncLog.SOURCE_QUEUE,
                        message = "Database restored successfully from cloud backup."
                    )
                )

                // 5. Force a full checkpoint and close to flush the log to the main file
                try {
                    db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
                } catch (e: Exception) {
                    Log.e("SyncCheck", "Checkpoint failed", e)
                }
                
                // Reset references again so the next access (after restart) is fresh
                app?.resetDatabaseReferences() ?: AppDatabase.closeAndReset()

                targetFile.delete()
            }

            // Finally, signal the UI that we are ready to restart
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
    private val userRepository: UserRepository,
    private val syncLogRepository: SyncLogRepository,
    private val isManualSync: Boolean = false
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SyncCheckViewModel(context, driveRepository, userRepository, syncLogRepository, isManualSync) as T
    }
}
