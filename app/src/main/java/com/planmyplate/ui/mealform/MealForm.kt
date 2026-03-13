package com.planmyplate.ui.mealform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planmyplate.PlanMyPlateApp
import com.planmyplate.model.MealType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealForm(sessionId: Long? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanMyPlateApp

    val viewModel: MealFormViewModel = viewModel(
        factory = MealFormViewModelFactory(app.mealRepository, sessionId)
    )
    val uiState by viewModel.uiState.collectAsState()

    val scrollState = rememberScrollState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.date.timeInMillis
    )

    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = uiState.hour,
        initialMinute = uiState.minute
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMealTypeMenu by remember { mutableStateOf(false) }

    // Consistent lighter border color
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledLabelColor = MaterialTheme.colorScheme.primary,
        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
    )

    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) {
            onBack()
        }
    }

    LaunchedEffect(uiState.dishes.size) {
        if (uiState.dishes.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (sessionId == null) "Add Meal" else "Edit Meal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sessionId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Meal", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                    Button(
                        onClick = { viewModel.saveMeal() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = uiState.dishes.isNotEmpty() || uiState.currentDishName.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (sessionId == null) "Schedule Meal" else "Update Meal", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Date Selection
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(uiState.date.time),
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Date") },
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true,
                    enabled = false,
                    colors = fieldColors
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDatePicker = true }
                )
            }

            // Meal Type Dropdown
            ExposedDropdownMenuBox(
                expanded = showMealTypeMenu,
                onExpandedChange = { showMealTypeMenu = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Meal Type") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMealTypeMenu) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                
                ExposedDropdownMenu(
                    expanded = showMealTypeMenu,
                    onDismissRequest = { showMealTypeMenu = false }
                ) {
                    MealType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                viewModel.onMealTypeSelected(type)
                                showMealTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Dishes Management
            Column {
                OutlinedTextField(
                    value = uiState.currentDishName,
                    onValueChange = { viewModel.onDishNameChanged(it) },
                    label = { Text("Add Dish") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Restaurant, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    trailingIcon = {
                        if (uiState.currentDishName.isNotBlank()) {
                            IconButton(onClick = { viewModel.addDish() }) {
                                Icon(
                                    Icons.Default.Add, 
                                    contentDescription = "Add Dish",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            if (uiState.currentDishName.isNotBlank()) {
                                viewModel.addDish()
                            }
                        }
                    )
                )

                if (uiState.dishes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.dishes.forEach { dish ->
                        ListItem(
                            headlineContent = { Text(dish, fontWeight = FontWeight.Medium) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeDish(dish) }) {
                                    Icon(
                                        Icons.Default.Close, 
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                headlineColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            // Time Selection
            Box(modifier = Modifier.fillMaxWidth()) {
                val amPm = if (uiState.hour < 12) "AM" else "PM"
                val displayHour = if (uiState.hour % 12 == 0) 12 else uiState.hour % 12
                val timeStr = "%02d:%02d %s".format(displayHour, uiState.minute, amPm)
                
                OutlinedTextField(
                    value = timeStr,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Time") },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true,
                    enabled = false,
                    colors = fieldColors
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTimePicker = true }
                )
            }

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.onNotesChanged(it) },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("e.g. Low carb, extra protein...") },
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    // Deletion Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Meal") },
            text = { Text("Are you sure you want to delete this scheduled meal? This will also remove it from your Google Calendar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMeal()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        }
                        
                        viewModel.onDateSelected(localCal)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeSelected(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
