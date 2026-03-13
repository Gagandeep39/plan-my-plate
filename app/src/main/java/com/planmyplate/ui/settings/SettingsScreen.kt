package com.planmyplate.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.PlanMyPlateApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanMyPlateApp
    
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.userRepository)
    )
    val uiState by viewModel.uiState.collectAsState()

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleAuthorizationResult(result.resultCode == Activity.RESULT_OK)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Synchronization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Primary Account Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (uiState.userEmail != null) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Google Account",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                uiState.userEmail ?: "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.userEmail != null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                            )
                        }
                        if (uiState.userEmail != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Connected", tint = Color(0xFF4CAF50))
                        }
                    }

                    if (uiState.userEmail == null) {
                        Button(
                            onClick = { viewModel.signIn(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect Google Account")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.disconnectGoogle { } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("Disconnect Account")
                        }
                    }
                }
            }

            if (uiState.userEmail != null) {
                Text("Services", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                // Google Calendar Service
                ServiceCard(
                    title = "Google Calendar",
                    description = "Sync meals to your personal calendar",
                    isConnected = uiState.isGoogleConnected,
                    onConnect = {
                        viewModel.connectCalendar(context) { pendingIntent ->
                            authLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                        }
                    },
                    onDisconnect = { viewModel.disconnectCalendar() }
                )

                // Google Drive Service
                ServiceCard(
                    title = "Google Drive",
                    description = "Backup meal data as JSON",
                    isConnected = uiState.isDriveConnected,
                    onConnect = {
                        viewModel.connectDrive(context) { pendingIntent ->
                            authLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                        }
                    },
                    onDisconnect = { viewModel.disconnectDrive() }
                )
            }

            uiState.error?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                "App Version: 1.0.0",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ServiceCard(
    title: String,
    description: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (title.contains("Drive")) Icons.Default.Cloud else Icons.Default.Sync,
                contentDescription = null,
                tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (isConnected) {
                TextButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disable")
                }
            } else {
                TextButton(onClick = onConnect) {
                    Text("Enable")
                }
            }
        }
    }
}
