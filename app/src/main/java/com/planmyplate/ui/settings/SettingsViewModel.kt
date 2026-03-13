package com.planmyplate.ui.settings

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isGoogleConnected: Boolean = false,
    val userEmail: String? = null,
    val error: String? = null,
    val isSyncing: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val calendarScope = Scope(CalendarScopes.CALENDAR)

    fun checkGoogleConnection(context: Context) {
        val sharedPrefs = context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPrefs.getString("user_email", null)
        val isAuthorized = sharedPrefs.getBoolean("calendar_authorized", false)
        
        _uiState.update { 
            it.copy(
                isGoogleConnected = savedEmail != null && isAuthorized,
                userEmail = savedEmail,
                error = null
            )
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
                    val email = googleIdTokenCredential.id
                    
                    context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
                        .edit().putString("user_email", email).apply()

                    requestCalendarAuthorization(context, onAuthResolutionRequired)
                }
            } catch (e: GetCredentialException) {
                Log.e("SettingsViewModel", "Credential Manager error", e)
                _uiState.update { it.copy(error = "Sign-in failed: ${e.message}") }
            }
        }
    }

    private fun requestCalendarAuthorization(context: Context, onResolutionRequired: (android.app.PendingIntent) -> Unit) {
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(calendarScope))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    authorizationResult.pendingIntent?.let { onResolutionRequired(it) }
                } else {
                    context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("calendar_authorized", true).apply()
                    _uiState.update { it.copy(isGoogleConnected = true) }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Authorization failed", e)
                _uiState.update { it.copy(error = "Calendar access denied") }
            }
    }

    fun handleAuthorizationResult(context: Context, success: Boolean) {
        if (success) {
            context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("calendar_authorized", true).apply()
            checkGoogleConnection(context)
        } else {
            _uiState.update { it.copy(error = "Authorization cancelled or failed") }
        }
    }

    fun disconnectGoogle(context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            
            context.getSharedPreferences("plan_my_plate_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("user_email")
                .remove("calendar_authorized")
                .apply()
                
            _uiState.update { it.copy(isGoogleConnected = false, userEmail = null) }
            onDone()
        }
    }
}
