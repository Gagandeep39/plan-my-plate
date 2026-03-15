package com.planmyplate.ui.navigation

sealed class Screen(val route: String) {
    object SyncCheck : Screen("sync_check?fromSettings={fromSettings}") {
        fun createRoute(fromSettings: Boolean) = "sync_check?fromSettings=$fromSettings"
    }
    object Main : Screen("main")
    object Settings : Screen("settings")
    object SyncHistory : Screen("sync_history")
    object MealForm : Screen("meal_form?sessionId={sessionId}") {
        fun createRoute(sessionId: Long?) = if (sessionId != null) "meal_form?sessionId=$sessionId" else "meal_form"
    }
    object RecipeForm : Screen("recipe_form?recipeId={recipeId}") {
        fun createRoute(recipeId: Long?) = if (recipeId != null) "recipe_form?recipeId=$recipeId" else "recipe_form"
    }
}
