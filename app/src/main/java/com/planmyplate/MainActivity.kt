package com.planmyplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.planmyplate.ui.home.HomeScreen
import com.planmyplate.ui.mealform.MealForm
import com.planmyplate.ui.settings.SettingsScreen
import com.planmyplate.ui.theme.PlanMyPlateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanMyPlateTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "timeline") {
        composable("timeline") {
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
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
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
