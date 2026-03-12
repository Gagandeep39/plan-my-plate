package com.planmyplate.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.data.AppDatabase
import com.planmyplate.ui.TimelineViewModel
import com.planmyplate.ui.TimelineViewModelFactory
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
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
                // Calculation: For each day before today, count:
                // 1 Header + 1 Spacer + N Meals + 1 Add Button
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
                stickyHeader {
                    HeaderItem(
                        title = dayPlan.headerTitle,
                        subtitle = dayPlan.subtitle
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(dayPlan.meals) { index, meal ->
                    TimelineItem(
                        meal = meal,
                        isFirst = index == 0,
                        isLast = false // It's never the last because "Add" follows
                    )
                }
                
                item {
                    AddMealItem(
                        isFirst = dayPlan.meals.isEmpty(),
                        onAddClick = {
                            // TODO: Navigate to Entry Screen
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderItem(title: String, subtitle: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
