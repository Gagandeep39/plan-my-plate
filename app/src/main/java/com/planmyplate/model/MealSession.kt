package com.planmyplate.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

// --- 1. THE SESSION TABLE (The Calendar Event) ---
@Entity(tableName = "meal_sessions")
data class MealSession(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val scheduledTimestamp: Long,
    val mealType: String,
    val isCompleted: Boolean = false,
    val notes: String? = null,

    // Audit Fields
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// --- 2. THE BRIDGE TABLE (Many-to-Many Link) ---
// Connects a Session to a Recipe (or multiple recipes)
@Entity(
    tableName = "session_recipes",
    foreignKeys = [
        ForeignKey(
            entity = MealSession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE // Delete the meal? Delete this record.
        ),
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["recipeId"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL // Delete the recipe? Keep this record, just break the link.
        )
    ],
    indices = [Index("sessionId"), Index("recipeId")]
)
data class SessionRecipe(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val recipeId: Long?, // Nullable anchor back to the master cookbook

    val recipeNameSnapshot: String // The frozen name for the timeline UI
)

// --- 3. THE TIMELINE CONTAINER ---
data class SessionWithRecipes(
    @Embedded val session: MealSession,

    // We just pull the SessionRecipe directly now. No Junction needed!
    @Relation(
        parentColumn = "sessionId",
        entityColumn = "sessionId"
    )
    val recipes: List<SessionRecipe>
)