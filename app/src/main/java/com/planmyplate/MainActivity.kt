package com.planmyplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.planmyplate.ui.home.HomeScreen
import com.planmyplate.ui.mealform.MealForm
import com.planmyplate.ui.navigation.NavTransitions
import com.planmyplate.ui.recipes.RecipesScreen
import com.planmyplate.ui.settings.SettingsScreen
import com.planmyplate.ui.settings.SyncHistoryScreen
import com.planmyplate.ui.theme.PlanMyPlateTheme
import com.planmyplate.ui.sync.SyncCheckScreen
import com.planmyplate.ui.sync.SyncCheckState
import com.planmyplate.ui.sync.SyncCheckViewModel
import com.planmyplate.ui.sync.SyncCheckViewModelFactory

class MainActivity : ComponentActivity() {

    private val syncCheckViewModel: SyncCheckViewModel by viewModels {
        val app = application as PlanMyPlateApp
        SyncCheckViewModelFactory(this, app.driveRepository, app.userRepository, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the splash screen visible while checking for sync updates.
        // This avoids showing the "Checking for updates..." loading screen if the check is quick.
        splashScreen.setKeepOnScreenCondition {
            syncCheckViewModel.state.value is SyncCheckState.Checking
        }

        // Kick off a DB backup on every fresh start (worker handles cooldown + auth checks + change checks)
        (applicationContext as PlanMyPlateApp).userRepository.enqueueDbSync()

        setContent {
            PlanMyPlateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(syncCheckViewModel)
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
fun AppNavigation(syncCheckViewModel: SyncCheckViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in listOf("timeline", "recipes")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentDestination?.hierarchy?.any { it.route == "timeline" } == true,
                        onClick = {
                            navController.navigate("timeline") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Recipes") },
                        label = { Text("Recipes") },
                        selected = currentDestination?.hierarchy?.any { it.route == "recipes" } == true,
                        onClick = {
                            navController.navigate("recipes") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = "sync_check",
            modifier = Modifier.padding(innerPadding),
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
                route = "recipes"
            ) {
                RecipesScreen()
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
                
                // For manual sync from settings, create a fresh ViewModel instance.
                // For the initial launch, use the activity-scoped ViewModel.
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
}
