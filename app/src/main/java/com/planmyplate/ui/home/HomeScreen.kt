package com.planmyplate.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planmyplate.model.DayPlan
import com.planmyplate.model.Meal
import com.planmyplate.model.MealType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
    
    // Generate dates
    val dayPlans = remember {
        val list = mutableListOf<DayPlan>()
        val calendar = Calendar.getInstance()
        
        // Go back 2 days
        calendar.add(Calendar.DAY_OF_YEAR, -2)
        
        for (i in -2..2) {
            val dateStr = dateFormat.format(calendar.time)
            val title = when (i) {
                0 -> "Today's Plan"
                -1 -> "Yesterday"
                else -> dateStr
            }
            
            list.add(
                DayPlan(
                    headerTitle = title,
                    meals = listOf(
                        Meal("${i}_1", "Sample Breakfast $i", 8, 0, MealType.BREAKFAST),
                        Meal("${i}_2", "Sample Lunch $i", 13, 0, MealType.LUNCH),
                        Meal("${i}_3", "Sample Dinner $i", 19, 0, MealType.DINNER)
                    ),
                    // Adding a helper for today identification
                    isToday = i == 0,
                    subtitle = if (i == 0) dateStr else null
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val listState = rememberLazyListState()

    // Scroll to Today on first load
    LaunchedEffect(Unit) {
        // Calculate the index for Today. 
        // Each day before today has: 1 Header + N Meals + 1 Spacer
        var todayIndex = 0
        for (plan in dayPlans) {
            if (plan.isToday) break
            todayIndex += 1 + plan.meals.size + 1
        }
        listState.scrollToItem(todayIndex)
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

                itemsIndexed(dayPlan.meals) { index, meal ->
                    TimelineItem(
                        meal = meal,
                        isLast = index == dayPlan.meals.size - 1
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun HeaderItem(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
