package com.planmyplate.ui.settings

import android.content.Context
import android.util.Base64
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
import com.planmyplate.data.repository.DriveRepository
import com.planmyplate.data.repository.SyncLogRepository
import com.planmyplate.data.repository.UserRepository
import com.planmyplate.model.SyncLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SettingsUiState(
    val isGoogleConnected: Boolean = false,
    val isDriveConnected: Boolean = false,
    val isDbSyncEnabled: Boolean = false,
    val userEmail: String? = null,
    val sharableLink: String? = null,
    val syncLogs: List<SyncLog> = emptyList(),
    val error: String? = null,
    val isLoadingLink: Boolean = false
)

private enum class AuthStep { SIGN_IN, CALENDAR_ONLY, DRIVE_ONLY }

class SettingsViewModel(
    private val userRepository: UserRepository,
    private val driveRepository: DriveRepository,
    private val syncLogRepository: SyncLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val calendarScope = Scope(CalendarScopes.CALENDAR)
    private val driveScope = Scope(DriveScopes.DRIVE_FILE)
    private var currentAuthStep = AuthStep.SIGN_IN

    private fun extractEmailFromIdToken(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        return try {
            val payloadPart = idToken.split(".").getOrNull(1) ?: return null
            val payload = String(Base64.decode(payloadPart, Base64.URL_SAFE or Base64.DEFAULT))
            JSONObject(payload).optString("email").takeIf { it.contains("@") }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeDriveLink(link: String): String {
        val regex = Regex("/file/d/([^/]+)")
        val match = regex.find(link) ?: return link
        val fileId = match.groupValues.getOrNull(1) ?: return link
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

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
            userRepository.sharableLink.collect { link ->
                val normalizedLink = link?.let { normalizeDriveLink(it) }
                if (link != null && normalizedLink != link) {
                    normalizedLink?.let { userRepository.saveSharableLink(it) }
                }
                _uiState.update { it.copy(sharableLink = normalizedLink) }
            }
        }
        viewModelScope.launch {
            userRepository.isDriveAuthorized.collect { authorized ->
                _uiState.update { it.copy(isDriveConnected = authorized) }
            }
        }
        viewModelScope.launch {
            userRepository.isDbSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(isDbSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            syncLogRepository.getAllSyncLogs().collect { logs ->
                _uiState.update { it.copy(syncLogs = logs) }
            }
        }
    }

    fun signIn(context: Context) {
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
                    val email = extractEmailFromIdToken(googleIdTokenCredential.idToken)
                        ?: googleIdTokenCredential.id.takeIf { it.contains("@") }
                        ?: credential.data.getString("email")?.takeIf { it.contains("@") }
                    if (email != null) {
                        userRepository.saveUser(email)
                    } else {
                        userRepository.setCalendarAuthorized(false)
                        userRepository.setDriveAuthorized(false)
                        _uiState.update { it.copy(error = "Google account email unavailable. Disconnect and connect again.") }
                    }
                }
            } catch (e: GetCredentialException) {
                Log.e("SettingsViewModel", "Credential Manager error", e)
                _uiState.update { it.copy(error = "Sign-in failed: ${e.message}") }
            }
        }
    }

    fun connectCalendar(context: Context, onResolutionRequired: (android.app.PendingIntent) -> Unit) {
        currentAuthStep = AuthStep.CALENDAR_ONLY
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(calendarScope))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    authorizationResult.pendingIntent?.let { onResolutionRequired(it) }
                } else {
                    userRepository.setCalendarAuthorized(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettingsViewModel", "Calendar authorization failed", e)
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
                AuthStep.CALENDAR_ONLY -> userRepository.setCalendarAuthorized(true)
                AuthStep.DRIVE_ONLY -> {
                    userRepository.setDriveAuthorized(true)
                }
                else -> {}
            }
        } else {
            _uiState.update { it.copy(error = "Authorization cancelled or failed") }
        }
    }

    // Verifies the Drive file still exists without touching the loading UI.
    // If the file was deleted externally, it gets re-created and the cache is updated.
    // On network failure we keep the cached link — it may still be valid.
    private fun silentlyVerifyDriveLink() {
        viewModelScope.launch {
            val freshLink = driveRepository.createOrGetSharableJsonFile()
            if (freshLink != null && freshLink != userRepository.sharableLink.value) {
                userRepository.saveSharableLink(freshLink)
            }
        }
    }

    fun refreshDriveLink() {
        if (_uiState.value.isLoadingLink) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLink = true, error = null) }
            val link = driveRepository.createOrGetSharableJsonFile()
            if (link != null) {
                userRepository.saveSharableLink(link)
                userRepository.enqueueDriveExportSync()
                _uiState.update { it.copy(isLoadingLink = false) }
            } else {
                _uiState.update { it.copy(isLoadingLink = false, error = "Could not load Drive link. Tap retry to try again.") }
            }
        }
    }

    fun disconnectCalendar() {
        userRepository.setCalendarAuthorized(false)
    }

    fun setDbSyncEnabled(enabled: Boolean) {
        userRepository.setDbSyncEnabled(enabled)
        if (enabled) userRepository.enqueueDbSync()
    }

    fun syncDbNow() {
        userRepository.enqueueDbSyncForced()
    }

    fun disconnectDrive() {
        userRepository.setDriveAuthorized(false)
        userRepository.clearSharableLink()
    }

    fun disconnectGoogle(onDone: () -> Unit) {
        viewModelScope.launch {
            userRepository.logout()
            _uiState.update { it.copy(sharableLink = null) }
            onDone()
        }
    }
}

class SettingsViewModelFactory(
    private val userRepository: UserRepository,
    private val driveRepository: DriveRepository,
    private val syncLogRepository: SyncLogRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userRepository, driveRepository, syncLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
