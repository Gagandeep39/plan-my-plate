package com.planmyplate.ui.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val hasCalendarScope = account != null && GoogleSignIn.hasPermissions(account, calendarScope)
        
        _uiState.update { 
            it.copy(
                isGoogleConnected = hasCalendarScope,
                userEmail = account?.email,
                error = null
            )
        }
    }

    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(calendarScope)
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val hasCalendarScope = GoogleSignIn.hasPermissions(account, calendarScope)
            
            if (account != null && hasCalendarScope) {
                _uiState.update { 
                    it.copy(
                        isGoogleConnected = true,
                        userEmail = account.email,
                        error = null
                    )
                }
            } else if (account != null && !hasCalendarScope) {
                _uiState.update { it.copy(error = "Calendar access was not granted. Please try again and accept all permissions.") }
            }
        } catch (e: ApiException) {
            val message = when (e.statusCode) {
                10 -> "Developer Error (10): Ensure your SHA-1 fingerprint is registered in the Google Cloud Console."
                7 -> "Network Error: Check your internet connection."
                12500 -> "Sign-in Failed (12500): Common issue with configuration or play services."
                12501 -> "Sign-in Cancelled"
                else -> "Sign-in failed: ${e.statusCode}. ${e.message}"
            }
            Log.e("SettingsViewModel", message, e)
            _uiState.update { it.copy(error = message) }
        }
    }

    fun disconnectGoogle(context: Context, onDone: () -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut().addOnCompleteListener {
            _uiState.update { it.copy(isGoogleConnected = false, userEmail = null, error = null) }
            onDone()
        }
    }
}
