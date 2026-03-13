package com.planmyplate.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
    val listState = rememberLazyListState()

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
                        "Plan My Plate",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                    onAddMealClick = onAddMeal,
                    onMealClick = onEditMeal
                )
            }
        }
    }
}
