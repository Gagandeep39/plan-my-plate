package com.planmyplate.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.work.WorkManager
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
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _userEmail = MutableStateFlow(sharedPrefs.getString(KEY_USER_EMAIL, null))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _isCalendarAuthorized = MutableStateFlow(sharedPrefs.getBoolean(KEY_CALENDAR_AUTHORIZED, false))
    val isCalendarAuthorized: StateFlow<Boolean> = _isCalendarAuthorized.asStateFlow()

    private val _isDriveAuthorized = MutableStateFlow(sharedPrefs.getBoolean(KEY_DRIVE_AUTHORIZED, false))
    val isDriveAuthorized: StateFlow<Boolean> = _isDriveAuthorized.asStateFlow()

    private val _sharableLink = MutableStateFlow(sharedPrefs.getString(KEY_DRIVE_SHARABLE_LINK, null))
    val sharableLink: StateFlow<String?> = _sharableLink.asStateFlow()

    fun saveUser(email: String) {
        sharedPrefs.edit().putString(KEY_USER_EMAIL, email).apply()
        _userEmail.value = email
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
            WorkManager.getInstance(context).cancelAllWorkByTag(DriveExportWorker.DRIVE_EXPORT_WORK_TAG)
        }
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
        // Clear local state immediately so the UI updates right away
        sharedPrefs.edit().clear().apply()
        _userEmail.value = null
        _isCalendarAuthorized.value = false
        _isDriveAuthorized.value = false
        _sharableLink.value = null
        // Credential manager clear is slow — run it after UI state is already updated
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    fun isLoggedIn(): Boolean = userEmail.value != null
}
