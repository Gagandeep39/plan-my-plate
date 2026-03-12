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
    onAddMealClick: () -> Unit
) {
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
        MealCard(
            meal = meal,
            isFirst = index == 0,
            isLast = false
        )
    }

    item {
        AddMealCard(
            isFirst = dayPlan.meals.isEmpty(),
            onAddClick = onAddMealClick
        )
    }
}
