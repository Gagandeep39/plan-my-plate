package com.planmyplate.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.work.WorkManager
import com.planmyplate.data.worker.DriveDbSyncWorker
import com.planmyplate.data.worker.DriveExportWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserRepository(private val context: Context) {
    companion object {
        const val PREFS_NAME = "plan_my_plate_prefs"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_CALENDAR_AUTHORIZED = "calendar_authorized"
        const val KEY_DRIVE_AUTHORIZED = "drive_authorized"
        const val KEY_DRIVE_SHARABLE_LINK = "drive_sharable_link"
        const val KEY_DB_SYNC_ENABLED = "db_sync_enabled"
        const val KEY_DB_LAST_SYNC_TIMESTAMP = "db_last_sync_timestamp"
        const val KEY_DB_LAST_UPLOAD_TIMESTAMP = "db_last_upload_timestamp"
        const val KEY_DB_LAST_WRITE_TIMESTAMP = "db_last_write_timestamp"
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun sanitizeEmail(value: String?): String? =
        value?.trim()?.takeIf { it.contains("@") }
    
    private val _userEmail = MutableStateFlow(sanitizeEmail(sharedPrefs.getString(KEY_USER_EMAIL, null)))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _isCalendarAuthorized = MutableStateFlow(sharedPrefs.getBoolean(KEY_CALENDAR_AUTHORIZED, false))
    val isCalendarAuthorized: StateFlow<Boolean> = _isCalendarAuthorized.asStateFlow()

    private val _isDriveAuthorized = MutableStateFlow(sharedPrefs.getBoolean(KEY_DRIVE_AUTHORIZED, false))
    val isDriveAuthorized: StateFlow<Boolean> = _isDriveAuthorized.asStateFlow()

    private val _sharableLink = MutableStateFlow(sharedPrefs.getString(KEY_DRIVE_SHARABLE_LINK, null))
    val sharableLink: StateFlow<String?> = _sharableLink.asStateFlow()

    private val _isDbSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean(KEY_DB_SYNC_ENABLED, false))
    val isDbSyncEnabled: StateFlow<Boolean> = _isDbSyncEnabled.asStateFlow()

    init {
        if (sharedPrefs.getString(KEY_USER_EMAIL, null) != _userEmail.value) {
            sharedPrefs.edit().putString(KEY_USER_EMAIL, _userEmail.value).apply()
        }
    }

    fun saveUser(email: String) {
        val safeEmail = sanitizeEmail(email) ?: return
        sharedPrefs.edit().putString(KEY_USER_EMAIL, safeEmail).commit()
        _userEmail.value = safeEmail
    }

    fun setCalendarAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_CALENDAR_AUTHORIZED, authorized).apply()
        _isCalendarAuthorized.value = authorized
        if (!authorized) {
            WorkManager.getInstance(context).cancelAllWorkByTag(MealRepository.CALENDAR_SYNC_WORK_TAG)
        }
    }

    fun setDriveAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_DRIVE_AUTHORIZED, authorized).apply()
        _isDriveAuthorized.value = authorized
        if (!authorized) {
            sharedPrefs.edit()
                .remove(KEY_DB_SYNC_ENABLED)
                .remove(KEY_DRIVE_SHARABLE_LINK)
                .apply()
            _isDbSyncEnabled.value = false
            _sharableLink.value = null
            WorkManager.getInstance(context).cancelAllWorkByTag(DriveExportWorker.DRIVE_EXPORT_WORK_TAG)
            WorkManager.getInstance(context).cancelAllWorkByTag(DriveDbSyncWorker.DRIVE_DB_SYNC_WORK_TAG)
        }
    }

    fun setDbSyncEnabled(enabled: Boolean) {
        val effectiveEnabled = enabled && _isDriveAuthorized.value
        sharedPrefs.edit().putBoolean(KEY_DB_SYNC_ENABLED, effectiveEnabled).apply()
        _isDbSyncEnabled.value = effectiveEnabled
        if (!effectiveEnabled) {
            WorkManager.getInstance(context).cancelAllWorkByTag(DriveDbSyncWorker.DRIVE_DB_SYNC_WORK_TAG)
        }
    }

    /** Enqueues a DB backup only when both Drive is authorised and the toggle is on. */
    fun enqueueDbSync() {
        if (!_isDriveAuthorized.value || !_isDbSyncEnabled.value) return
        DriveDbSyncWorker.enqueue(context)
    }

    fun markLocalWrite() {
        sharedPrefs.edit()
            .putLong(KEY_DB_LAST_WRITE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun recordUploadTimestamp(ts: Long = System.currentTimeMillis()) {
        sharedPrefs.edit()
            .putLong(KEY_DB_LAST_UPLOAD_TIMESTAMP, ts)
            .putLong(KEY_DB_LAST_SYNC_TIMESTAMP, ts) // keep legacy key in sync
            .apply()
    }

    fun getLastUploadTimestamp(): Long =
        sharedPrefs.getLong(KEY_DB_LAST_UPLOAD_TIMESTAMP, 0L)

    fun getLastWriteTimestamp(): Long =
        sharedPrefs.getLong(KEY_DB_LAST_WRITE_TIMESTAMP, 0L)

    /** Force-enqueues a DB backup, bypassing cooldown. Requires Drive authorised + toggle on. */
    fun enqueueDbSyncForced() {
        if (!_isDriveAuthorized.value || !_isDbSyncEnabled.value) return
        DriveDbSyncWorker.enqueueForced(context)
    }

    fun enqueueDriveExportSync() {
        DriveExportWorker.enqueue(context)
    }

    fun saveSharableLink(link: String) {
        sharedPrefs.edit().putString(KEY_DRIVE_SHARABLE_LINK, link).apply()
        _sharableLink.value = link
    }

    fun clearSharableLink() {
        sharedPrefs.edit().remove(KEY_DRIVE_SHARABLE_LINK).apply()
        _sharableLink.value = null
    }

    suspend fun logout() {
        // Remove only relevant keys for a clean reset
        sharedPrefs.edit()
            .remove(KEY_USER_EMAIL)
            .remove(KEY_CALENDAR_AUTHORIZED)
            .remove(KEY_DRIVE_AUTHORIZED)
            .remove(KEY_DRIVE_SHARABLE_LINK)
            .remove(KEY_DB_SYNC_ENABLED)
            .remove(KEY_DB_LAST_SYNC_TIMESTAMP)
            .remove(KEY_DB_LAST_UPLOAD_TIMESTAMP)
            .remove(KEY_DB_LAST_WRITE_TIMESTAMP)
            .apply()
        _userEmail.value = null
        _isCalendarAuthorized.value = false
        _isDriveAuthorized.value = false
        _sharableLink.value = null
        _isDbSyncEnabled.value = false
        WorkManager.getInstance(context).cancelAllWorkByTag(DriveDbSyncWorker.DRIVE_DB_SYNC_WORK_TAG)
        // Credential manager clear is slow — run it after UI state is already updated
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    fun isLoggedIn(): Boolean = userEmail.value != null
}
