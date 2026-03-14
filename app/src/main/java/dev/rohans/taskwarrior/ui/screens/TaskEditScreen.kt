package dev.rohans.taskwarrior.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import uniffi.taskgeneral_core.TaskUpdate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    navController: NavController,
    uuid: String?,
    viewModel: TaskViewModel
) {
    var description by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var currentTagInput by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var isPriorityExpanded by remember { mutableStateOf(false) }

    var dueDate by remember { mutableStateOf("") }
    var waitDate by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("") }
    var isRecurrenceExpanded by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showWaitDatePicker by remember { mutableStateOf(false) }

    var isActive by remember { mutableStateOf(false) }
    var isNext by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    val isNewTask = uuid == null || uuid == "new"

    LaunchedEffect(uuid) {
        if (!isNewTask) {
            isLoading = true
            viewModel.getTask(uuid!!) { task ->
                if (task != null) {
                    description = task.description
                    project = task.project ?: ""
                    tags = task.tags
                    priority = task.priority ?: ""
                    dueDate = task.due ?: ""
                    waitDate = task.wait ?: ""
                    recurrence = task.recur ?: ""
                    isActive = task.isActive
                    isNext = task.tags.contains("next")
                }
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewTask) "Add Task" else "Edit Task") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.popBackStack() }
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        shape = RectangleShape,
                        modifier = Modifier.weight(1f),
                        enabled = description.isNotBlank() && !isLoading,
                        onClick = {
                            val updates = TaskUpdate(
                                description = description,
                                project = if (project.isBlank()) null else project,
                                tags = tags,
                                priority = priority,
                                due = if (dueDate.isBlank()) null else dueDate,
                                wait = if (waitDate.isBlank()) null else waitDate,
                                recur = if (recurrence.isBlank()) null else recurrence
                            )

                            if (isNewTask) {
                                viewModel.createTask(description) { newTask ->
                                    if (project.isNotBlank() || tags.isNotEmpty() || priority.isNotBlank() || dueDate.isNotBlank() || waitDate.isNotBlank() || recurrence.isNotBlank()) {
                                        viewModel.updateTask(newTask.uuid, updates) {
                                            navController.popBackStack()
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            } else {
                                viewModel.updateTask(uuid!!, updates) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = description.isBlank(),
                    supportingText = {
                        if (description.isBlank()) {
                            Text("Description is required", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = isPriorityExpanded,
                    onExpandedChange = { isPriorityExpanded = it },
                ) {
                    OutlinedTextField(
                        value = when(priority) {
                            "L" -> "Low"
                            "M" -> "Medium"
                            "H" -> "High"
                            else -> "None"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPriorityExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isPriorityExpanded,
                        onDismissRequest = { isPriorityExpanded = false }
                    ) {
                        listOf("" to "None", "L" to "Low", "M" to "Medium", "H" to "High").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    priority = key
                                    isPriorityExpanded = false
                                }
                            )
                        }
                    }
                }

                if (showDueDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDueDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    dueDate = millisToRfc3339(millis)
                                }
                                showDueDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                OutlinedTextField(
                    value = if (dueDate.isBlank()) "Not set" else formatRfc3339ForDisplay(dueDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Due Date") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (dueDate.isNotBlank()) {
                                IconButton(onClick = { dueDate = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear due date")
                                }
                            }
                            IconButton(onClick = { showDueDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick due date")
                            }
                        }
                    }
                )

                if (showWaitDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showWaitDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    waitDate = millisToRfc3339(millis)
                                }
                                showWaitDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showWaitDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                OutlinedTextField(
                    value = if (waitDate.isBlank()) "Not set" else formatRfc3339ForDisplay(waitDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Wait Until") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            if (waitDate.isNotBlank()) {
                                IconButton(onClick = { waitDate = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear wait date")
                                }
                            }
                            IconButton(onClick = { showWaitDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick wait date")
                            }
                        }
                    }
                )

                ExposedDropdownMenuBox(
                    expanded = isRecurrenceExpanded,
                    onExpandedChange = { isRecurrenceExpanded = it },
                ) {
                    OutlinedTextField(
                        value = when(recurrence) {
                            "daily" -> "Daily"
                            "weekly" -> "Weekly"
                            "biweekly" -> "Biweekly"
                            "monthly" -> "Monthly"
                            "quarterly" -> "Quarterly"
                            "yearly" -> "Yearly"
                            else -> "None"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Recurrence") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRecurrenceExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isRecurrenceExpanded,
                        onDismissRequest = { isRecurrenceExpanded = false }
                    ) {
                        listOf("" to "None", "daily" to "Daily", "weekly" to "Weekly", "biweekly" to "Biweekly", "monthly" to "Monthly", "quarterly" to "Quarterly", "yearly" to "Yearly").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    recurrence = key
                                    isRecurrenceExpanded = false
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentTagInput,
                        onValueChange = { input ->
                            if (input.endsWith(",") || input.endsWith("\n")) {
                                val newTag = input.trim().dropLast(1)
                                if (newTag.isNotBlank() && !tags.contains(newTag)) {
                                    tags = tags + newTag
                                }
                                currentTagInput = ""
                            } else {
                                currentTagInput = input
                            }
                        },
                        label = { Text("Tags (Type and press ',' or Enter)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    if (tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tags) { tag ->
                                InputChip(
                                    shape = RectangleShape,
                                    selected = true,
                                    onClick = { },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close, 
                                            contentDescription = "Remove tag", 
                                            modifier = Modifier.size(16.dp).clickable { tags = tags - tag }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isNewTask) {
                    HorizontalDivider()
                    Text(
                        text = "Task Actions",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            shape = RectangleShape,
                            onClick = {
                                if (isActive) {
                                    viewModel.stopTask(uuid!!)
                                    isActive = false
                                } else {
                                    viewModel.startTask(uuid!!)
                                    isActive = true
                                }
                            },
                            colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors().let {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        ) {
                            Text(if (isActive) "Stop Task" else "Start Task")
                        }
                        FilterChip(
                            shape = RectangleShape,
                            selected = isNext,
                            onClick = {
                                viewModel.toggleNext(uuid!!)
                                isNext = !isNext
                            },
                            label = { Text("Next") }
                        )
                    }
                }
            }
        }
    }
}

private fun formatRfc3339ForDisplay(rfc3339: String): String {
    return try {
        val instant = java.time.Instant.parse(rfc3339)
        instant.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        rfc3339
    }
}

private fun millisToRfc3339(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ISO_INSTANT)
}
