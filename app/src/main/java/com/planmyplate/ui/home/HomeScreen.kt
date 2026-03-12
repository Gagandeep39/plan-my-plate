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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
    
    val dayPlans = remember {
        val list = mutableListOf<DayPlan>()
        val calendar = Calendar.getInstance()
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
                    isToday = i == 0,
                    subtitle = if (i == 0) dateStr else null
                )
            )
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        var todayIndex = 0
        for (plan in dayPlans) {
            if (plan.isToday) break
            // Header + Spacer + Meals
            todayIndex += 1 + 1 + plan.meals.size
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

                // Gap between Header and First Item
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(dayPlan.meals) { index, meal ->
                    TimelineItem(
                        meal = meal,
                        isFirst = index == 0,
                        isLast = index == dayPlan.meals.size - 1
                    )
                }
                
                // No extra spacer here: the TimelineItem's built-in spacer 
                // now handles the gap to the next header consistently.
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
