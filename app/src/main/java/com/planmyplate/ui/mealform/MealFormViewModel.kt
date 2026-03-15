package com.planmyplate.ui.mealform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.data.repository.MealRepository
import com.planmyplate.data.repository.RecipeRepository
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealType
import com.planmyplate.model.Recipe
import com.planmyplate.model.SessionRecipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class MealFormUiState(
    val sessionId: Long? = null,
    val date: Calendar = Calendar.getInstance(),
    val mealType: MealType = MealType.BREAKFAST,
    val hour: Int = 9,
    val minute: Int = 0,
    val selectedRecipes: List<SessionRecipe> = emptyList(), // Now using SessionRecipe directly
    val recipeSearchQuery: String = "",
    val searchResults: List<Recipe> = emptyList(),
    val notes: String = "",
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val mealSession: MealSession? = null
)

class MealFormViewModel(
    private val mealRepository: MealRepository,
    private val recipeRepository: RecipeRepository,
    private val initialSessionId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealFormUiState(sessionId = initialSessionId))
    val uiState: StateFlow<MealFormUiState> = _uiState.asStateFlow()

    init {
        if (initialSessionId != null) {
            loadMeal(initialSessionId)
        }
    }

    private fun loadMeal(id: Long) {
        viewModelScope.launch {
            mealRepository.getAllMeals().collect { allMeals ->
                val swr = allMeals.find { it.session.sessionId == id }
                swr?.let { sessionWithRecipes ->
                    val cal = Calendar.getInstance().apply { timeInMillis = sessionWithRecipes.session.scheduledTimestamp }
                    
                    _uiState.update { 
                        it.copy(
                            date = cal,
                            mealType = try { MealType.valueOf(sessionWithRecipes.session.mealType) } catch (e: Exception) { MealType.LUNCH },
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE),
                            selectedRecipes = sessionWithRecipes.recipes, // No mapping needed!
                            notes = sessionWithRecipes.session.notes ?: "",
                            mealSession = sessionWithRecipes.session
                        )
                    }
                }
            }
        }
    }

    fun onDateSelected(calendar: Calendar) {
        _uiState.update { it.copy(date = calendar) }
    }

    fun onMealTypeSelected(type: MealType) {
        val (defaultHour, defaultMinute) = when (type) {
            MealType.BREAKFAST -> 9 to 0
            MealType.LUNCH -> 13 to 0
            MealType.SNACK -> 16 to 0
            MealType.DINNER -> 18 to 30
        }
        _uiState.update { it.copy(mealType = type, hour = defaultHour, minute = defaultMinute) }
    }

    fun onTimeSelected(hour: Int, minute: Int) {
        _uiState.update { it.copy(hour = hour, minute = minute) }
    }

    fun onRecipeSearchQueryChanged(query: String) {
        _uiState.update { it.copy(recipeSearchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
            } else {
                val results = recipeRepository.searchRecipes(query)
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun selectRecipe(recipe: Recipe) {
        // Only add if not already selected (check recipeId for existing links)
        val isAlreadySelected = _uiState.value.selectedRecipes.any { 
            it.recipeId != null && it.recipeId == recipe.recipeId 
        }

        if (!isAlreadySelected) {
            val snapshot = SessionRecipe(
                sessionId = _uiState.value.sessionId ?: 0,
                recipeId = recipe.recipeId,
                recipeNameSnapshot = recipe.name
            )
            _uiState.update {
                it.copy(
                    selectedRecipes = it.selectedRecipes + snapshot,
                    recipeSearchQuery = "",
                    searchResults = emptyList()
                )
            }
        }
    }

    fun removeRecipe(sessionRecipe: SessionRecipe) {
        _uiState.update {
            it.copy(selectedRecipes = it.selectedRecipes - sessionRecipe)
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.selectedRecipes.isEmpty()) return@launch

            val calendar = currentState.date.clone() as Calendar
            calendar.set(Calendar.HOUR_OF_DAY, currentState.hour)
            calendar.set(Calendar.MINUTE, currentState.minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val session = MealSession(
                sessionId = currentState.sessionId ?: 0,
                scheduledTimestamp = calendar.timeInMillis,
                mealType = currentState.mealType.name,
                notes = currentState.notes.ifBlank { null }
            )
            
            mealRepository.saveMeal(session, currentState.selectedRecipes)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteMeal() {
        viewModelScope.launch {
            val session = _uiState.value.mealSession
            if (session != null) {
                mealRepository.deleteMeal(session)
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }
}

class MealFormViewModelFactory(
    private val mealRepository: MealRepository,
    private val recipeRepository: RecipeRepository,
    private val sessionId: Long?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MealFormViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MealFormViewModel(mealRepository, recipeRepository, sessionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
