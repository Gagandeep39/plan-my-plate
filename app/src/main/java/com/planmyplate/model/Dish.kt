package com.planmyplate.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dishes")
data class Dish(
    @PrimaryKey(autoGenerate = true)
    val dishId: Long = 0,
    val parentSessionId: Long, // The bridge!
    val dishName: String,      // Renamed!
    val recipeId: Long? = null
)
