package com.planmyplate.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserRepository(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
    
    private val _userEmail = MutableStateFlow(sharedPrefs.getString("user_email", null))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _isCalendarAuthorized = MutableStateFlow(sharedPrefs.getBoolean("calendar_authorized", false))
    val isCalendarAuthorized: StateFlow<Boolean> = _isCalendarAuthorized.asStateFlow()

    private val _isDriveAuthorized = MutableStateFlow(sharedPrefs.getBoolean("drive_authorized", false))
    val isDriveAuthorized: StateFlow<Boolean> = _isDriveAuthorized.asStateFlow()

    private val _sharableLink = MutableStateFlow(sharedPrefs.getString("drive_sharable_link", null))
    val sharableLink: StateFlow<String?> = _sharableLink.asStateFlow()

    fun saveUser(email: String) {
        sharedPrefs.edit().putString("user_email", email).apply()
        _userEmail.value = email
    }

    fun setCalendarAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean("calendar_authorized", authorized).apply()
        _isCalendarAuthorized.value = authorized
        if (!authorized) {
            WorkManager.getInstance(context).cancelAllWorkByTag(MealRepository.CALENDAR_SYNC_WORK_TAG)
        }
    }

    fun setDriveAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean("drive_authorized", authorized).apply()
        _isDriveAuthorized.value = authorized
    }

    fun saveSharableLink(link: String) {
        sharedPrefs.edit().putString("drive_sharable_link", link).apply()
        _sharableLink.value = link
    }

    fun clearSharableLink() {
        sharedPrefs.edit().remove("drive_sharable_link").apply()
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
