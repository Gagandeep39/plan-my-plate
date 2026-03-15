package com.planmyplate.ui.recipes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.planmyplate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipes") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Coming Soon!",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
