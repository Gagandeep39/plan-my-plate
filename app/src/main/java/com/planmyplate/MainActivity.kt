package com.planmyplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.planmyplate.ui.EntryScreen
import com.planmyplate.ui.home.HomeScreen
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
            HomeScreen(onAddMeal = {
                navController.navigate("entry")
            })
        }
        composable("entry") {
            EntryScreen(onBack = {
                navController.popBackStack()
            })
        }
    }
}
