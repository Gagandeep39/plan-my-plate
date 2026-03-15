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
        startDestination = "sync_check",
        enterTransition = { NavTransitions.enterTransition(this) },
        exitTransition = { NavTransitions.exitTransition(this) },
        popEnterTransition = { NavTransitions.popEnterTransition(this) },
        popExitTransition = { NavTransitions.popExitTransition(this) }
    ) {
        composable(
            route = "main",
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
                onAddMeal = { navController.navigate("meal_form") },
                onEditMeal = { sessionId -> navController.navigate("meal_form?sessionId=$sessionId") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable(
            route = "sync_check?fromSettings={fromSettings}",
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
                        navController.navigate("main") {
                            popUpTo("sync_check?fromSettings={fromSettings}") { inclusive = true }
                        }
                    }
                },
                onRestoreComplete = {
                    activity?.recreate()
                    navController.navigate("main") {
                        popUpTo("sync_check?fromSettings={fromSettings}") { inclusive = true }
                    }
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSyncHistory = { navController.navigate("sync_history") },
                onNavigateToSyncCheck = {
                    navController.navigate("sync_check?fromSettings=true")
                }
            )
        }

        composable("sync_history") {
            SyncHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "meal_form?sessionId={sessionId}",
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
    }
}
