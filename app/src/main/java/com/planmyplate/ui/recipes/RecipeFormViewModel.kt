package com.planmyplate.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.data.repository.RecipeRepository
import com.planmyplate.model.Ingredient
import com.planmyplate.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IngredientDraft(
    val id: Long = 0,
    val name: String = "",
    val amount: String = "",
    val createdAt: Long? = null
)

data class RecipeFormUiState(
    val recipeId: Long? = null,
    val name: String = "",
    val steps: String = "",
    val comments: String = "",
    val durationMinutes: String = "",
    val difficulty: String? = null,
    val ingredients: List<IngredientDraft> = emptyList(), // Start empty as requested
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val showErrors: Boolean = false,
    val existingRecipe: Recipe? = null
)

class RecipeFormViewModel(
    private val repository: RecipeRepository,
    private val initialRecipeId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeFormUiState(recipeId = initialRecipeId))
    val uiState: StateFlow<RecipeFormUiState> = _uiState.asStateFlow()

    init {
        if (initialRecipeId != null) {
            loadRecipe(initialRecipeId)
        }
    }

    private fun loadRecipe(id: Long) {
        viewModelScope.launch {
            repository.getRecipeWithIngredients(id)?.let { rwi ->
                _uiState.update {
                    it.copy(
                        name = rwi.recipe.name,
                        steps = rwi.recipe.steps,
                        comments = rwi.recipe.comments ?: "",
                        durationMinutes = rwi.recipe.durationMinutes?.toString() ?: "",
                        difficulty = rwi.recipe.difficulty,
                        existingRecipe = rwi.recipe,
                        ingredients = rwi.ingredients.map { ing -> 
                            IngredientDraft(
                                id = ing.ingredientId, 
                                name = ing.name, 
                                amount = ing.amount,
                                createdAt = ing.createdAt
                            ) 
                        }
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onStepsChanged(steps: String) {
        _uiState.update { it.copy(steps = steps) }
    }

    fun onCommentsChanged(comments: String) {
        _uiState.update { it.copy(comments = comments) }
    }

    fun onDurationChanged(duration: String) {
        _uiState.update { it.copy(durationMinutes = duration) }
    }

    fun onDifficultyChanged(difficulty: String?) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun addIngredient() {
        _uiState.update { it.copy(ingredients = it.ingredients + IngredientDraft()) }
    }

    fun removeIngredient(index: Int) {
        _uiState.update {
            val newList = it.ingredients.toMutableList()
            newList.removeAt(index)
            it.copy(ingredients = newList)
        }
    }

    fun updateIngredient(index: Int, name: String, amount: String) {
        _uiState.update {
            val newList = it.ingredients.toMutableList()
            newList[index] = newList[index].copy(name = name, amount = amount)
            it.copy(ingredients = newList)
        }
    }

    fun saveRecipe() {
        val currentState = _uiState.value
        
        // Validation
        val hasInvalidIngredients = currentState.ingredients.any { it.name.isBlank() }
        if (currentState.name.isBlank() || hasInvalidIngredients) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }

        viewModelScope.launch {
            val recipe = Recipe(
                recipeId = currentState.recipeId ?: 0,
                name = currentState.name,
                steps = currentState.steps,
                comments = currentState.comments.ifBlank { null },
                durationMinutes = currentState.durationMinutes.toIntOrNull(),
                difficulty = currentState.difficulty,
                createdAt = currentState.existingRecipe?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val ingredients = currentState.ingredients
                .map {
                    Ingredient(
                        ingredientId = it.id,
                        parentRecipeId = currentState.recipeId ?: 0,
                        name = it.name,
                        amount = it.amount,
                        createdAt = it.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }

            repository.upsertRecipe(recipe, ingredients)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteRecipe() {
        viewModelScope.launch {
            val id = _uiState.value.recipeId
            if (id != null) {
                repository.deleteRecipe(id)
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }
}

class RecipeFormViewModelFactory(
    private val repository: RecipeRepository,
    private val recipeId: Long?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipeFormViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecipeFormViewModel(repository, recipeId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
