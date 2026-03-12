package com.planmyplate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.data.MealDao
import com.planmyplate.model.Dish
import com.planmyplate.model.MealSession
import com.planmyplate.model.MealType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

data class EntryUiState(
    val sessionId: Long? = null,
    val date: Calendar = Calendar.getInstance(),
    val mealType: MealType = MealType.BREAKFAST,
    val hour: Int = 9,
    val minute: Int = 0,
    val dishes: List<String> = emptyList(),
    val currentDishName: String = "",
    val notes: String = "",
    val isSaved: Boolean = false
)

class EntryViewModel(private val mealDao: MealDao, private val initialSessionId: Long?) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState(sessionId = initialSessionId))
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        if (initialSessionId != null) {
            loadMeal(initialSessionId)
        }
    }

    private fun loadMeal(id: Long) {
        viewModelScope.launch {
            // In a real app, we'd have a getMealById in DAO. 
            // For now, we'll find it from the list or just assume it exists if navigated to.
            // Since getAllMeals is a Flow, we can collect it once to find our meal.
            mealDao.getAllMeals().collect { allMeals ->
                val mwd = allMeals.find { it.session.sessionId == id }
                mwd?.let { mealWithDishes ->
                    val cal = Calendar.getInstance().apply { timeInMillis = mealWithDishes.session.scheduledTimestamp }
                    _uiState.update { 
                        it.copy(
                            date = cal,
                            mealType = try { MealType.valueOf(mealWithDishes.session.mealType) } catch (e: Exception) { MealType.LUNCH },
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE),
                            dishes = mealWithDishes.dishes.map { d -> d.dishName },
                            notes = mealWithDishes.session.notes ?: ""
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
            MealType.DINNER -> 18 to 30
        }
        _uiState.update { it.copy(mealType = type, hour = defaultHour, minute = defaultMinute) }
    }

    fun onTimeSelected(hour: Int, minute: Int) {
        _uiState.update { it.copy(hour = hour, minute = minute) }
    }

    fun onDishNameChanged(name: String) {
        _uiState.update { it.copy(currentDishName = name) }
    }

    fun addDish() {
        if (_uiState.value.currentDishName.isNotBlank()) {
            _uiState.update {
                it.copy(
                    dishes = it.dishes + it.currentDishName.trim(),
                    currentDishName = ""
                )
            }
        }
    }

    fun removeDish(dishName: String) {
        _uiState.update {
            it.copy(dishes = it.dishes - dishName)
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val finalDishes = if (currentState.currentDishName.isNotBlank()) {
                currentState.dishes + currentState.currentDishName.trim()
            } else {
                currentState.dishes
            }

            if (finalDishes.isEmpty()) return@launch

            val calendar = currentState.date.clone() as Calendar
            calendar.set(Calendar.HOUR_OF_DAY, currentState.hour)
            calendar.set(Calendar.MINUTE, currentState.minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val session = MealSession(
                sessionId = currentState.sessionId ?: 0, // 0 for auto-gen in Room if not specified
                scheduledTimestamp = calendar.timeInMillis,
                mealType = currentState.mealType.name,
                notes = currentState.notes.ifBlank { null }
            )
            
            val sessionId = mealDao.insertSession(session)

            // For editing, we should ideally clear old dishes or update them. 
            // Simplifying: if it's an edit, we'd need to handle dish syncing.
            // For now, let's just insert.
            val dishesToInsert = finalDishes.map { dishName ->
                Dish(parentSessionId = sessionId, dishName = dishName)
            }
            if (dishesToInsert.isNotEmpty()) {
                mealDao.insertDishes(dishesToInsert)
            }

            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

class EntryViewModelFactory(private val mealDao: MealDao, private val sessionId: Long?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EntryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EntryViewModel(mealDao, sessionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
