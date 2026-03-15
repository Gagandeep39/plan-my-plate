package com.planmyplate.data.repository

import android.content.Context
import androidx.room.Transaction
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.RecipeDao
import com.planmyplate.model.Ingredient
import com.planmyplate.model.Recipe
import com.planmyplate.model.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val context: Context) {
    private val recipeDao: RecipeDao
        get() = AppDatabase.getDatabase(context).recipeDao()

    fun getAllRecipes(): Flow<List<RecipeWithIngredients>> = recipeDao.getAllRecipes()

    suspend fun getRecipeWithIngredients(recipeId: Long): RecipeWithIngredients? =
        recipeDao.getRecipeWithIngredients(recipeId)

    @Transaction
    suspend fun upsertRecipe(recipe: Recipe, ingredients: List<Ingredient>): Long {
        val upsertedId = recipeDao.upsertRecipe(recipe)
        
        // If upsertedId is -1, it means Room performed an UPDATE.
        // In that case, we must use the recipeId from the object passed in.
        val recipeId = if (upsertedId == -1L) recipe.recipeId else upsertedId
        
        recipeDao.deleteIngredientsForRecipe(recipeId)
        val ingredientsToInsert = ingredients.map { it.copy(parentRecipeId = recipeId, ingredientId = 0) }
        if (ingredientsToInsert.isNotEmpty()) {
            recipeDao.insertIngredients(ingredientsToInsert)
        }
        
        return recipeId
    }

    suspend fun deleteRecipe(recipeId: Long) = recipeDao.deleteRecipe(recipeId)

    suspend fun searchRecipes(query: String): List<Recipe> = recipeDao.searchRecipes(query)
}
