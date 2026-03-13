package com.planmyplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.planmyplate.ui.home.HomeScreen
import com.planmyplate.ui.mealform.MealForm
import com.planmyplate.ui.navigation.NavTransitions
import com.planmyplate.ui.settings.SettingsScreen
import com.planmyplate.ui.settings.SyncHistoryScreen
import com.planmyplate.ui.theme.PlanMyPlateTheme
import com.planmyplate.ui.sync.SyncCheckScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition before calling super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kick off a DB backup on every fresh start (worker handles cooldown + auth checks + change checks)
        (applicationContext as PlanMyPlateApp).userRepository.enqueueDbSync()

        setContent {
            PlanMyPlateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Back up DB whenever the app moves to background / is closed
        (applicationContext as PlanMyPlateApp).userRepository.enqueueDbSync()
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController, 
        startDestination = "sync_check",
        enterTransition = NavTransitions.enterTransition,
        exitTransition = NavTransitions.exitTransition,
        popEnterTransition = NavTransitions.popEnterTransition,
        popExitTransition = NavTransitions.popExitTransition
    ) {
        composable(
            route = "timeline"
        ) {
            HomeScreen(
                onAddMeal = {
                    navController.navigate("meal_form")
                },
                onEditMeal = { sessionId ->
                    navController.navigate("meal_form?sessionId=$sessionId")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }
        composable(
            route = "sync_check?fromSettings={fromSettings}",
            arguments = listOf(
                navArgument("fromSettings") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            val activity = (LocalContext.current as? android.app.Activity)
            SyncCheckScreen(
                isManualSync = fromSettings,
                onClear = {
                    if (fromSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate("timeline") {
                            popUpTo("sync_check?fromSettings={fromSettings}") { inclusive = true }
                        }
                    }
                },
                onRestoreComplete = {
                    activity?.recreate()
                    navController.navigate("timeline") {
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
