package com.planmyplate.data

import androidx.room.*
import com.planmyplate.model.Ingredient
import com.planmyplate.model.Recipe
import com.planmyplate.model.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipes(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeId = :recipeId")
    suspend fun getRecipeWithIngredients(recipeId: Long): RecipeWithIngredients?

    @Upsert
    suspend fun upsertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<Ingredient>)

    @Query("DELETE FROM ingredients WHERE parentRecipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Long)

    @Query("DELETE FROM recipes WHERE recipeId = :recipeId")
    suspend fun deleteRecipe(recipeId: Long)

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :query || '%'")
    suspend fun searchRecipes(query: String): List<Recipe>
}
