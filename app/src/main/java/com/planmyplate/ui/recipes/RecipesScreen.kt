package com.planmyplate.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.model.Recipe

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onAddRecipe: () -> Unit,
    onEditRecipe: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanMyPlateApp
    val viewModel: RecipesViewModel = viewModel(
        factory = RecipesViewModelFactory(app.recipeRepository)
    )

    val recipesWithIngredients by viewModel.recipes.collectAsState(initial = emptyList())
    var recipeToDelete by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Recipes", fontWeight = FontWeight.Bold)
                        val count = recipesWithIngredients.size
                        val countText = "$count recipes"
                        Text(
                            countText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRecipe) {
                Icon(Icons.Default.Add, contentDescription = "Add Recipe")
            }
        }
    ) { paddingValues ->
        if (recipesWithIngredients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recipes yet. Tap + to add one!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(0.dp),
            ) {
                items(recipesWithIngredients.size, key = { idx -> recipesWithIngredients[idx].recipe.recipeId }) { index ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    val item = recipesWithIngredients[index]
                    RecipeItem(
                        recipe = item.recipe,
                        ingredients = item.ingredients,
                        onClick = { onEditRecipe(item.recipe.recipeId) },
                        onDelete = { recipeToDelete = item.recipe.recipeId }
                    )
                }
            }
        }

        // Delete confirmation dialog
        if (recipeToDelete != null) {
            AlertDialog(
                onDismissRequest = { recipeToDelete = null },
                title = { Text("Delete Recipe") },
                text = { Text("Are you sure you want to delete this recipe?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteRecipe(recipeToDelete!!)
                        recipeToDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recipeToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun RecipeItem(
    recipe: Recipe,
    ingredients: List<com.planmyplate.model.Ingredient>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val difficultyColor = when (recipe.difficulty) {
        "Easy" -> Color(0xFF4CAF50)
        "Medium" -> Color(0xFFFF9800)
        "Hard" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 0.dp),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!recipe.difficulty.isNullOrBlank()) {
                    Surface(
                        color = difficultyColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.SignalCellularAlt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = difficultyColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = recipe.difficulty,
                                style = MaterialTheme.typography.labelSmall,
                                color = difficultyColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        supportingContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (recipe.durationMinutes != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${recipe.durationMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (ingredients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        ingredients.forEach { ingredient ->
                            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = ingredient.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                if (ingredient.amount.isNotBlank()) {
                                    Text(
                                        text = " (${ingredient.amount})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // No trailingContent (delete button removed)
    )
}
