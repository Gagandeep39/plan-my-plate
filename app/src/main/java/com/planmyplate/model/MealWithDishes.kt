package com.planmyplate.model

import androidx.room.Embedded
import androidx.room.Relation

data class MealWithDishes(
    @Embedded val session: MealSession,
    @Relation(
        parentColumn = "sessionId",
        entityColumn = "parentSessionId"
    )
    val dishes: List<Dish>
)
