package com.planmyplate.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.planmyplate.ui.home.HomeScreen
import com.planmyplate.ui.recipes.RecipesScreen

enum class MainTab {
    HOME, RECIPES
}

@Composable
fun MainScreen(
    onAddMeal: () -> Unit,
    onEditMeal: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == MainTab.HOME,
                    onClick = { selectedTab = MainTab.HOME }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.RestaurantMenu, contentDescription = "Recipes") },
                    label = { Text("Recipes") },
                    selected = selectedTab == MainTab.RECIPES,
                    onClick = { selectedTab = MainTab.RECIPES }
                )
            }
        }
    ) { innerPadding ->
        // We apply bottom padding to ensure content isn't hidden by the NavigationBar
        Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    onAddMeal = onAddMeal,
                    onEditMeal = onEditMeal,
                    onOpenSettings = onOpenSettings
                )
                MainTab.RECIPES -> RecipesScreen()
            }
        }
    }
}
