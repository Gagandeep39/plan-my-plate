package com.planmyplate.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.data.AppDatabase
import com.planmyplate.ui.TimelineViewModel
import com.planmyplate.ui.TimelineViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onAddMeal: () -> Unit, onEditMeal: (Long) -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val viewModel: TimelineViewModel = viewModel(
        factory = TimelineViewModelFactory(database.mealDao())
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
