package com.planmyplate.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import com.planmyplate.model.MealType

object MealTypeIcons {
    fun getColor(type: MealType): Color {
        return when (type) {
            MealType.BREAKFAST -> Color(0xFFB26A00) // Warm amber
            MealType.LUNCH -> Color(0xFF1565C0) // Strong blue
            MealType.SNACK -> Color(0xFFAD1457) // Rich pink
            MealType.DINNER -> Color(0xFF2E7D32) // Balanced green
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
