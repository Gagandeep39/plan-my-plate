package com.planmyplate.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.ui.main.MainScreen
import com.planmyplate.ui.mealform.MealForm
import com.planmyplate.ui.recipes.RecipeForm
import com.planmyplate.ui.settings.SettingsScreen
import com.planmyplate.ui.settings.SyncHistoryScreen
import com.planmyplate.ui.sync.SyncCheckScreen
import com.planmyplate.ui.sync.SyncCheckViewModel
import com.planmyplate.ui.sync.SyncCheckViewModelFactory

@Composable
fun AppNavigation(syncCheckViewModel: SyncCheckViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.SyncCheck.route,
        enterTransition = { NavTransitions.enterTransition(this) },
        exitTransition = { NavTransitions.exitTransition(this) },
        popEnterTransition = { NavTransitions.popEnterTransition(this) },
        popExitTransition = { NavTransitions.popExitTransition(this) }
    ) {
        composable(
            route = Screen.Main.route,
            enterTransition = {
                if (initialState.destination.route?.contains("sync_check") == true) {
                    EnterTransition.None
                } else {
                    NavTransitions.enterTransition(this)
                }
            }
        ) {
            MainScreen(
                onAddMeal = { navController.navigate(Screen.MealForm.createRoute(null)) },
                onEditMeal = { sessionId -> navController.navigate(Screen.MealForm.createRoute(sessionId)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onAddRecipe = { navController.navigate(Screen.RecipeForm.createRoute(null)) },
                onEditRecipe = { recipeId -> navController.navigate(Screen.RecipeForm.createRoute(recipeId)) }
            )
        }

        composable(
            route = Screen.SyncCheck.route,
            arguments = listOf(
                navArgument("fromSettings") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            exitTransition = {
                if (targetState.destination.route?.contains("main") == true) {
                    ExitTransition.None
                } else {
                    NavTransitions.exitTransition(this)
                }
            }
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            
            val vm = if (fromSettings) {
                val app = context.applicationContext as PlanMyPlateApp
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SyncCheckViewModelFactory(context, app.driveRepository, app.userRepository, app.syncLogRepository, true)
                )
            } else {
                syncCheckViewModel
            }

            SyncCheckScreen(
                viewModel = vm,
                onClear = {
                    if (fromSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.SyncCheck.route) { inclusive = true }
                        }
                    }
                },
                onRestoreComplete = {
                    // Full Activity Restart is still required to swap the DB file safely
                    context.findActivity()?.let { activity ->
                        val intent = activity.intent
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        activity.startActivity(intent)
                        activity.finish()
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSyncHistory = { navController.navigate(Screen.SyncHistory.route) },
                onNavigateToSyncCheck = {
                    // Navigate to SyncCheck and CLEAR everything else to prevent DB access crashes
                    navController.navigate(Screen.SyncCheck.createRoute(true)) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SyncHistory.route) {
            SyncHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.MealForm.route,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull()
            MealForm(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RecipeForm.route,
            arguments = listOf(
                navArgument("recipeId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId")?.toLongOrNull()
            RecipeForm(
                recipeId = recipeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
