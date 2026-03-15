package com.planmyplate.data.repository

import android.content.Context
import com.planmyplate.data.AppDatabase
import com.planmyplate.data.RecipeDao
import com.planmyplate.model.Recipe
import com.planmyplate.model.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val context: Context) {
    private val recipeDao: RecipeDao
        get() = AppDatabase.getDatabase(context).recipeDao()

    fun getAllRecipes(): Flow<List<RecipeWithIngredients>> = recipeDao.getAllRecipes()

    suspend fun getRecipeWithIngredients(recipeId: Long): RecipeWithIngredients? =
        recipeDao.getRecipeWithIngredients(recipeId)

    suspend fun upsertRecipe(recipe: Recipe): Long = recipeDao.upsertRecipe(recipe)

    suspend fun deleteRecipe(recipeId: Long) = recipeDao.deleteRecipe(recipeId)

    suspend fun searchRecipes(query: String): List<Recipe> = recipeDao.searchRecipes(query)
}
