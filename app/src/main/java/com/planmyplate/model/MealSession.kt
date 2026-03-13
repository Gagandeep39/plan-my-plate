package com.planmyplate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_sessions")
data class MealSession(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val scheduledTimestamp: Long, 
    val mealType: String, // e.g., "BREAKFAST", "LUNCH", "DINNER"
    val isCompleted: Boolean = false,
    val notes: String? = null
)
