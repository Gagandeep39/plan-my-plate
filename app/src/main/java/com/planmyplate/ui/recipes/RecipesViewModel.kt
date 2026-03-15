package com.planmyplate.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.data.repository.RecipeRepository
import com.planmyplate.model.Recipe
import com.planmyplate.model.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RecipesViewModel(private val repository: RecipeRepository) : ViewModel() {

    val recipes: Flow<List<RecipeWithIngredients>> = repository.getAllRecipes()

    fun addRecipe(name: String) {
        viewModelScope.launch {
            repository.upsertRecipe(Recipe(name = name, steps = ""))
        }
    }

    fun deleteRecipe(recipeId: Long) {
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
        }
    }
}

class RecipesViewModelFactory(private val repository: RecipeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecipesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
