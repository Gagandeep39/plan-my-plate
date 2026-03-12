package com.planmyplate.model

data class Meal(
    val id: String,
    val name: String,
    val hour: Int,
    val minute: Int,
    val type: MealType
) {
    val formattedTime: String
        get() {
            val amPm = if (hour < 12) "AM" else "PM"
            val h = if (hour % 12 == 0) 12 else hour % 12
            return "%02d:%02d %s".format(h, minute, amPm)
        }
}

enum class MealType {
    BREAKFAST, LUNCH, DINNER
}

data class DayPlan(
    val headerTitle: String,
    val meals: List<Meal>,
    val isToday: Boolean = false,
    val subtitle: String? = null
)
