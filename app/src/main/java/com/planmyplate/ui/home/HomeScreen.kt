package com.planmyplate.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.PlanMyPlateApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMeal: () -> Unit, 
    onEditMeal: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanMyPlateApp
    val viewModel: TimelineViewModel = viewModel(
        factory = TimelineViewModelFactory(app.mealRepository)
    )
    
    val dayPlans by viewModel.timelineState.collectAsState()
    val selectedMealIds by viewModel.selectedMealIds.collectAsState()
    val listState = rememberLazyListState()

    val isSelectionMode = selectedMealIds.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        BackHandler {
            viewModel.clearSelection()
        }
    }

    LaunchedEffect(dayPlans) {
        if (dayPlans.isNotEmpty()) {
            val todayIndex = dayPlans.indexOfFirst { it.isToday }
            if (todayIndex != -1) {
                var scrollTarget = 0
                for (i in 0 until todayIndex) {
                    scrollTarget += 1 + 1 + dayPlans[i].meals.size + 1
                }
                listState.scrollToItem(scrollTarget)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) "${selectedMealIds.size} Selected" else "Plan My Plate",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            dayPlans.forEach { dayPlan ->
                DailyMealSection(
                    dayPlan = dayPlan,
                    selectedMealIds = selectedMealIds,
                    onAddMealClick = onAddMeal,
                    onMealClick = onEditMeal,
                    onMealLongClick = { viewModel.toggleSelection(it) }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Meals") },
            text = { Text("Are you sure you want to delete ${selectedMealIds.size} selected meal(s)? This will also remove them from your Google Calendar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedMeals()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
