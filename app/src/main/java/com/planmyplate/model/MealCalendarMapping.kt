package com.planmyplate.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_calendar_mappings",
    foreignKeys = [
        ForeignKey(
            entity = MealSession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MealCalendarMapping(
    @PrimaryKey
    val sessionId: Long,
    val calendarEventId: String
)
