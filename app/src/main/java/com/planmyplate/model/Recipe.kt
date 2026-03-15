package com.planmyplate.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val recipeId: Long = 0,
    val name: String,
    val steps: String,
    val comments: String? = null,
    val durationMinutes: Int? = null,
    val difficulty: String? = null, // Easy, Medium, Hard
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["recipeId"],
            childColumns = ["parentRecipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentRecipeId")]
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true)
    val ingredientId: Long = 0,
    val parentRecipeId: Long,
    val name: String,
    val amount: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

// Container to get a full Recipe + Ingredients
data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(
        parentColumn = "recipeId",
        entityColumn = "parentRecipeId"
    )
    val ingredients: List<Ingredient>
)
