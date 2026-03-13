package com.planmyplate.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.planmyplate.model.MealType

object MealTypeIcons {
    fun getColor(type: MealType): Color {
        return when (type) {
            MealType.BREAKFAST -> Color(0xFFFFF9C4) // Light Yellow
            MealType.LUNCH -> Color(0xFFE1F5FE) // Light Blue
            MealType.SNACK -> Color(0xFFFCE4EC) // Light Pink
            MealType.DINNER -> Color(0xFFE8F5E9) // Light Green
        }
    }

    fun getIcon(type: MealType): ImageVector {
        return when (type) {
            MealType.BREAKFAST -> Icons.Default.BakeryDining
            MealType.LUNCH -> Icons.Default.LunchDining
            MealType.SNACK -> Icons.Default.Icecream
            MealType.DINNER -> Icons.Default.DinnerDining
        }
    }
}
