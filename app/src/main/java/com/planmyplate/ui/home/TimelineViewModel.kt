package com.planmyplate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.planmyplate.data.repository.MealRepository
import com.planmyplate.model.DayPlan
import com.planmyplate.model.Meal
import com.planmyplate.model.MealType
import com.planmyplate.model.MealWithDishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TimelineViewModel(private val repository: MealRepository) : ViewModel() {

    private val _selectedMealIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMealIds: StateFlow<Set<String>> = _selectedMealIds.asStateFlow()

    private val _allMealsWithDishes = MutableStateFlow<List<MealWithDishes>?>(null)

    private val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        viewModelScope.launch {
            repository.getAllMeals().collect {
                _allMealsWithDishes.value = it
            }
        }
    }

    val timelineState: StateFlow<List<DayPlan>> = _allMealsWithDishes
        .filterNotNull()
        .map { mealsWithDishes ->
            withContext(Dispatchers.Default) {
                generateTimeline(mealsWithDishes)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun toggleSelection(mealId: String) {
        _selectedMealIds.update { current ->
            if (current.contains(mealId)) current - mealId else current + mealId
        }
    }

    fun clearSelection() {
        _selectedMealIds.value = emptySet()
    }

    fun deleteSelectedMeals() {
        viewModelScope.launch {
            val idsToDelete = _selectedMealIds.value
            val mealsToDelete = (_allMealsWithDishes.value ?: emptyList()).filter { it.session.sessionId.toString() in idsToDelete }
            
            mealsToDelete.forEach { 
                repository.deleteMeal(it.session)
            }
            clearSelection()
        }
    }

    private fun generateTimeline(mealsWithDishes: List<MealWithDishes>): List<DayPlan> {
        val todayCalendar = Calendar.getInstance()
        val todayDateStr = dateFormat.format(todayCalendar.time)
        
        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayDateStr = dateFormat.format(yesterdayCalendar.time)

        val dataByDate = mealsWithDishes.groupBy { 
            val cal = Calendar.getInstance().apply { timeInMillis = it.session.scheduledTimestamp }
            isoFormat.format(cal.time)
        }

        val fullTimeline = mutableListOf<DayPlan>()
        val genCalendar = Calendar.getInstance()
        genCalendar.add(Calendar.DAY_OF_YEAR, -10) 

        for (i in 0..20) { 
            val time = genCalendar.time
            val currentIsoDate = isoFormat.format(time)
            val currentDateStr = dateFormat.format(time)
            
            val mealsForDate = dataByDate[currentIsoDate] ?: emptyList()
            
            val title = when (currentDateStr) {
                todayDateStr -> "Today's Plan"
                yesterdayDateStr -> "Yesterday"
                else -> currentDateStr
            }

            fullTimeline.add(
                DayPlan(
                    headerTitle = title,
                    meals = mealsForDate.map { mwd ->
                        val cal = Calendar.getInstance().apply { timeInMillis = mwd.session.scheduledTimestamp }
                        Meal(
                            id = mwd.session.sessionId.toString(),
                            name = mwd.dishes.joinToString(", ") { it.dishName }.ifEmpty { "No dishes added" },
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE),
                            type = try { MealType.valueOf(mwd.session.mealType) } catch (e: Exception) { MealType.LUNCH }
                        )
                    }.sortedBy { it.hour * 60 + it.minute },
                    isToday = currentDateStr == todayDateStr,
                    subtitle = if (currentDateStr == todayDateStr) currentDateStr else null
                )
            )
            genCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Sort: Today always at the top
        val todayIndex = fullTimeline.indexOfFirst { it.isToday }
        if (todayIndex > 0) {
            val today = fullTimeline.removeAt(todayIndex)
            fullTimeline.add(0, today)
        }

        return fullTimeline
    }
}

class TimelineViewModelFactory(private val repository: MealRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimelineViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
