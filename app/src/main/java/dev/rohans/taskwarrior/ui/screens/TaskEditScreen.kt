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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.rohans.taskwarrior.utils.EncryptedPreferencesHelper
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import dev.rohans.taskwarrior.viewmodel.TaskViewModelFactory
import uniffi.taskgeneral_core.TaskUpdate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    navController: NavController,
    uuid: String?,
    viewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(
            LocalContext.current.filesDir,
            EncryptedPreferencesHelper.getEncryptedSharedPreferences(LocalContext.current),
            LocalContext.current
        )
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var description by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var currentTagInput by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }
    var isPriorityExpanded by remember { mutableStateOf(false) }
    
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
                                priority = priority
                            )

                            if (isNewTask) {
                                viewModel.createTask(description) { newTask ->
                                    if (project.isNotBlank() || tags.isNotEmpty() || priority.isNotBlank()) {
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
            }
        }
    }
}
