package dev.rohans.taskwarrior.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.rohans.taskwarrior.navigation.Route
import dev.rohans.taskwarrior.ui.components.FilterRow
import dev.rohans.taskwarrior.ui.components.TaskItem
import dev.rohans.taskwarrior.utils.EncryptedPreferencesHelper
import dev.rohans.taskwarrior.viewmodel.TaskOperation
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import dev.rohans.taskwarrior.viewmodel.TaskViewModelFactory
import uniffi.taskgeneral_core.TaskFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    navController: NavController,
    viewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(
            LocalContext.current.filesDir,
            EncryptedPreferencesHelper.getEncryptedSharedPreferences(LocalContext.current),
            LocalContext.current
        )
    )
) {
    val tasks by viewModel.tasks.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val availableProjects by viewModel.availableProjects.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val snackbarEvent by viewModel.snackbarEvent.collectAsState()
    val selectedUuids by viewModel.selectedUuids.collectAsState()
    val activeOperation by viewModel.activeOperation.collectAsState()
    val context = LocalContext.current
    
    val pullRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val isSelectionMode = selectedUuids.isNotEmpty() || activeOperation != null

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(Unit) {
        val activity = (context as? android.app.Activity)
        val hasSeed = activity?.intent?.getBooleanExtra("seed", false) == true
        if (hasSeed) {
            viewModel.seedDebugTasks()
        }
    }

    LaunchedEffect(snackbarEvent) {
        val event = snackbarEvent ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = event.actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            if (event.taskUuids != null) {
                viewModel.batchUncomplete(event.taskUuids)
            } else if (event.taskUuid != null) {
                viewModel.uncompleteTask(event.taskUuid)
            }
        }
        viewModel.clearSnackbar()
    }
    
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadTasks()
            pullRefreshState.endRefresh()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                    Text(
                        text = "${selectedUuids.size} selected",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Box(modifier = Modifier.padding(end = 8.dp))
                }
            } else {
                TopAppBar(
                    title = { Text("TaskGeneral") },
                    actions = {
                        IconButton(onClick = {
                            navController.navigate(Route.SyncSettings.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { },
                            enabled = false
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Reports")
                        }

                        IconButton(
                            onClick = {
                                if (isSelectionMode && activeOperation == TaskOperation.DELETE) {
                                } else {
                                    viewModel.enterSelectionMode(TaskOperation.DELETE)
                                }
                            },
                            colors = if (activeOperation == TaskOperation.DELETE) 
                                IconButtonDefaults.filledIconButtonColors() 
                            else 
                                IconButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = if (activeOperation == TaskOperation.DELETE) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isSelectionMode && activeOperation == TaskOperation.COMPLETE) {
                                } else {
                                    viewModel.enterSelectionMode(TaskOperation.COMPLETE)
                                }
                            },
                            colors = if (activeOperation == TaskOperation.COMPLETE) 
                                IconButtonDefaults.filledIconButtonColors() 
                            else 
                                IconButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Complete",
                                tint = if (activeOperation == TaskOperation.COMPLETE) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isSelectionMode) {
                            IconButton(
                                onClick = { viewModel.executeOperation() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Confirm")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    navController.navigate(Route.TaskEdit.createRoute(null)) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New Task")
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FilterRow(
                            currentFilter = filter,
                            availableProjects = availableProjects,
                            availableTags = availableTags,
                            onFilterChange = { newFilter -> viewModel.setFilter(newFilter) }
                        )
                    }

                    if (tasks.isEmpty() && !isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No tasks found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = tasks,
                            key = { it.task.uuid }
                        ) { item ->
                            TaskItem(
                                item = item,
                                isSelected = selectedUuids.contains(item.task.uuid),
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleTaskSelection(item.task.uuid)
                                    } else {
                                        navController.navigate(Route.TaskEdit.createRoute(item.task.uuid)) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.enterSelectionMode(TaskOperation.COMPLETE)
                                    }
                                    viewModel.toggleTaskSelection(item.task.uuid)
                                }
                            )
                        }
                    }
                }
            }
            
            if (isLoading) {
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

