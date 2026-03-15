package com.planmyplate.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// --- 1. THE RECIPE TABLE ---
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Long = 0,
    val name: String,
    val steps: String,
    val comments: String? = null,

    // Audit Fields
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// --- 2. THE INGREDIENT TABLE ---
@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["recipeId"],
            childColumns = ["parentRecipeId"],
            onDelete = ForeignKey.CASCADE // Deleting a recipe deletes its ingredients
        )
    ],
    indices = [Index("parentRecipeId")] // Required by Room to keep queries fast
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true)
    val ingredientId: Long = 0,
    val parentRecipeId: Long, // Links back to the Recipe
    val name: String,
    val amount: String,

    // Audit Fields
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// --- 3. THE CONTAINER (Recipe + Ingredients) ---
// Not a table! Just a way for Room to bundle the query results.
data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,

    @Relation(
        parentColumn = "recipeId",
        entityColumn = "parentRecipeId"
    )
    val ingredients: List<Ingredient>
)