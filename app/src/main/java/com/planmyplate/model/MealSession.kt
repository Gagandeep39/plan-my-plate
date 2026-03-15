package com.planmyplate.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

// --- 1. THE SESSION TABLE (The Calendar Event) ---
@Entity(tableName = "meal_sessions")
data class MealSession(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val scheduledTimestamp: Long, // Saved as UTC Epoch milliseconds
    val mealType: String,         // e.g., "BREAKFAST", "LUNCH"
    val isCompleted: Boolean = false,
    val notes: String? = null,

    // Audit Fields
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// --- 2. THE BRIDGE TABLE (Many-to-Many Link) ---
// Connects a Session to a Recipe (or multiple recipes)
@Entity(
    tableName = "session_recipe_cross_ref",
    primaryKeys = ["sessionId", "recipeId"],
    indices = [Index("recipeId")]
)
data class SessionRecipeCrossRef(
    val sessionId: Long,
    val recipeId: Long
)

// --- 3. THE MASTER TIMELINE CONTAINER ---
// This grabs the Session, crosses the bridge, and grabs the full Recipes (with their ingredients)
data class SessionWithRecipes(
    @Embedded val session: MealSession,

    @Relation(
        parentColumn = "sessionId",
        entityColumn = "recipeId",
        associateBy = Junction(SessionRecipeCrossRef::class)
    )
    val recipes: List<RecipeWithIngredients> // Reuses the container from Recipe.kt!
)