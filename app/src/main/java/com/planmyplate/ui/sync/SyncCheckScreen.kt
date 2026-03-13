package com.planmyplate.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.PlanMyPlateApp

@Composable
fun SyncCheckScreen(
    onClear: () -> Unit,
    onRestoreComplete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanMyPlateApp
    val viewModel: SyncCheckViewModel = viewModel(
        factory = SyncCheckViewModelFactory(context, app.driveRepository, app.userRepository)
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is SyncCheckState.Clear, SyncCheckState.Skipped -> onClear()
            is SyncCheckState.RestoreComplete -> onRestoreComplete()
            else -> Unit
        }
    }

    when (val s = state) {
        is SyncCheckState.Checking, SyncCheckState.Skipped, SyncCheckState.Clear -> {
            SyncLoadingScreen("Checking for updates…")
        }

        is SyncCheckState.Restoring -> {
            SyncLoadingScreen("Restoring your data…")
        }

        is SyncCheckState.RestoreComplete -> {
            SyncLoadingScreen("Done. Reloading…")
        }

        is SyncCheckState.Conflict -> {
            ConflictResolutionScreen(
                localTimestamp = viewModel.formatTimestamp(s.localTimestamp),
                cloudTimestamp = viewModel.formatTimestamp(s.cloudTimestamp),
                onKeepLocal = { viewModel.keepLocal() },
                onUseCloud = { viewModel.useCloud() }
            )
        }
    }
}

@Composable
private fun SyncLoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConflictResolutionScreen(
    localTimestamp: String,
    cloudTimestamp: String,
    onKeepLocal: () -> Unit,
    onUseCloud: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            "Sync Conflict",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Your data was updated on another device. Which version would you like to keep?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Local option
            OutlinedCard(
                modifier = Modifier.weight(1f),
                onClick = onKeepLocal
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "This Device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        localTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Cloud option
            Card(
                modifier = Modifier.weight(1f),
                onClick = onUseCloud,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "Cloud",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        cloudTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            "Tap a card to select. The other version will be permanently discarded.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
