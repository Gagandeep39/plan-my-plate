package com.planmyplate.ui.settings

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes
import com.planmyplate.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isGoogleConnected: Boolean = false,
    val isDriveConnected: Boolean = false,
    val userEmail: String? = null,
    val error: String? = null,
    val isSyncing: Boolean = false
)

private enum class AuthStep { SIGN_IN, DRIVE_ONLY }

class SettingsViewModel(private val userRepository: UserRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val calendarScope = Scope(CalendarScopes.CALENDAR)
    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)
    private var currentAuthStep = AuthStep.SIGN_IN

    init {
        viewModelScope.launch {
            userRepository.userEmail.collect { email ->
                _uiState.update { it.copy(userEmail = email) }
            }
        }
        viewModelScope.launch {
            userRepository.isCalendarAuthorized.collect { authorized ->
                _uiState.update { it.copy(isGoogleConnected = authorized) }
            }
        }
        viewModelScope.launch {
            userRepository.isDriveAuthorized.collect { authorized ->
                _uiState.update { it.copy(isDriveConnected = authorized) }
            }
        }
    }

    fun signIn(context: Context, onAuthResolutionRequired: (android.app.PendingIntent) -> Unit) {
        val credentialManager = CredentialManager.create(context)
        val serverClientId = "468906434207-oj1asrjtq2htvbch6dls24vg4an7gs6g.apps.googleusercontent.com"

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    userRepository.saveUser(googleIdTokenCredential.id)
                    // Request Calendar authorization by default after sign-in
                    requestCalendarAuthorization(context, onAuthResolutionRequired)
                }
            } catch (e: GetCredentialException) {
                Log.e("SettingsViewModel", "Credential Manager error", e)
                _uiState.update { it.copy(error = "Sign-in failed: ${e.message}") }
            }
        }
    }

    private fun requestCalendarAuthorization(context: Context, onResolutionRequired: (android.app.PendingIntent) -> Unit) {
        currentAuthStep = AuthStep.SIGN_IN
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(calendarScope, driveScope))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    authorizationResult.pendingIntent?.let { onResolutionRequired(it) }
                } else {
                    userRepository.setCalendarAuthorized(true)
                    userRepository.setDriveAuthorized(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Authorization failed", e)
                _uiState.update { it.copy(error = "Calendar access denied") }
            }
    }

    fun connectDrive(context: Context, onResolutionRequired: (android.app.PendingIntent) -> Unit) {
        currentAuthStep = AuthStep.DRIVE_ONLY
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(driveScope))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    authorizationResult.pendingIntent?.let { onResolutionRequired(it) }
                } else {
                    userRepository.setDriveAuthorized(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Drive authorization failed", e)
                _uiState.update { it.copy(error = "Drive access denied") }
            }
    }

    fun handleAuthorizationResult(success: Boolean) {
        if (success) {
            when (currentAuthStep) {
                AuthStep.SIGN_IN -> {
                    userRepository.setCalendarAuthorized(true)
                    userRepository.setDriveAuthorized(true)
                }
                AuthStep.DRIVE_ONLY -> {
                    userRepository.setDriveAuthorized(true)
                }
            }
        } else {
            _uiState.update { it.copy(error = "Authorization cancelled or failed") }
        }
    }

    fun disconnectGoogle(onDone: () -> Unit) {
        viewModelScope.launch {
            userRepository.logout()
            onDone()
        }
    }
}

class SettingsViewModelFactory(private val userRepository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
