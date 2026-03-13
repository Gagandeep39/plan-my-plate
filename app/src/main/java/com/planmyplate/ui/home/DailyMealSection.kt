package com.planmyplate.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.planmyplate.model.DayPlan

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.DailyMealSection(
    dayPlan: DayPlan,
    selectedMealIds: Set<String>,
    onAddMealClick: () -> Unit,
    onMealClick: (Long) -> Unit,
    onMealLongClick: (String) -> Unit
) {
    // Adding a key to the sticky header for better stability
    stickyHeader(key = "header_${dayPlan.headerTitle}") {
        HeaderItem(
            title = dayPlan.headerTitle,
            subtitle = dayPlan.subtitle
        )
    }

    item {
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Adding stable keys to meal items is crucial for smooth scrolling and animations
    itemsIndexed(
        items = dayPlan.meals,
        key = { _, meal -> meal.id }
    ) { index, meal ->
        MealCard(
            meal = meal,
            isFirst = index == 0,
            isLast = false,
            isSelected = selectedMealIds.contains(meal.id),
            onClick = { 
                if (selectedMealIds.isNotEmpty()) {
                    onMealLongClick(meal.id)
                } else {
                    onMealClick(meal.id.toLong()) 
                }
            },
            onLongClick = { onMealLongClick(meal.id) }
        )
    }

    // Use a unique key for the "Add Meal" card as well
    item(key = "add_${dayPlan.headerTitle}") {
        AddMealCard(
            isFirst = dayPlan.meals.isEmpty(),
            onAddClick = onAddMealClick
        )
    }
}
