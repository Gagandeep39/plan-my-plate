package com.planmyplate.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
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

    fun saveUser(email: String) {
        sharedPrefs.edit().putString("user_email", email).apply()
        _userEmail.value = email
    }

    fun setCalendarAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean("calendar_authorized", authorized).apply()
        _isCalendarAuthorized.value = authorized
    }

    fun setDriveAuthorized(authorized: Boolean) {
        sharedPrefs.edit().putBoolean("drive_authorized", authorized).apply()
        _isDriveAuthorized.value = authorized
    }

    suspend fun logout() {
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        sharedPrefs.edit().clear().apply()
        _userEmail.value = null
        _isCalendarAuthorized.value = false
        _isDriveAuthorized.value = false
    }

    fun isLoggedIn(): Boolean = userEmail.value != null
}
