package com.planmyplate.ui.navigation

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

    NavHost(
        navController = navController,
        startDestination = Screen.SyncCheck.route,
        enterTransition = { NavTransitions.enterTransition(this) },
        exitTransition = { NavTransitions.exitTransition(this) },
        popEnterTransition = { NavTransitions.popEnterTransition(this) },
        popExitTransition = { NavTransitions.popExitTransition(this) }
    ) {
        composable(route = Screen.Main.route,
            enterTransition = {
                // Disable animation when coming from the initial splash/sync check for a seamless transition
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
                // Disable animation when moving to main to avoid the "sliding out" effect under the splash screen
                if (targetState.destination.route?.contains("main") == true) {
                    ExitTransition.None
                } else {
                    NavTransitions.exitTransition(this)
                }
            }
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            val activity = (LocalContext.current as? android.app.Activity)

            val vm = if (fromSettings) {
                val app = LocalContext.current.applicationContext as PlanMyPlateApp
                androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SyncCheckViewModelFactory(LocalContext.current, app.driveRepository, app.userRepository, true)
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
                    activity?.recreate()
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.SyncCheck.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSyncHistory = { navController.navigate(Screen.SyncHistory.route) },
                onNavigateToSyncCheck = {
                    navController.navigate(Screen.SyncCheck.createRoute(true))
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
                onBack = {
                    navController.popBackStack()
                }
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
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
