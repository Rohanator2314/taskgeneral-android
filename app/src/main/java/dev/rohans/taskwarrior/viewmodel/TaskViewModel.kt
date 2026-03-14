package dev.rohans.taskwarrior.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rohans.taskwarrior.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.taskgeneral_core.SortField
import uniffi.taskgeneral_core.SyncResult
import uniffi.taskgeneral_core.TaskFilter
import uniffi.taskgeneral_core.TaskInfo
import uniffi.taskgeneral_core.TaskUpdate
import uniffi.taskgeneral_core.WorkingSetItem
import java.util.UUID

enum class TaskOperation {
    DELETE, COMPLETE, UNCOMPLETE
}

class TaskViewModel(
    private val repository: TaskRepository,
    private val prefs: SharedPreferences,
    private val context: Context
) : ViewModel() {
    
    private val _tasks = MutableStateFlow<List<WorkingSetItem>>(emptyList())
    val tasks: StateFlow<List<WorkingSetItem>> = _tasks.asStateFlow()

    private val _availableProjects = MutableStateFlow<List<String>>(emptyList())
    val availableProjects: StateFlow<List<String>> = _availableProjects.asStateFlow()

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()
    
    private val _filter = MutableStateFlow(TaskFilter(null, null, null, null))
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    private val _sortField = MutableStateFlow(SortField.URGENCY)
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _snackbarEvent = MutableStateFlow<SnackbarEvent?>(null)
    val snackbarEvent: StateFlow<SnackbarEvent?> = _snackbarEvent.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _encryptionSecret = MutableStateFlow("")
    val encryptionSecret: StateFlow<String> = _encryptionSecret.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _selectedUuids = MutableStateFlow<Set<String>>(emptySet())
    val selectedUuids: StateFlow<Set<String>> = _selectedUuids.asStateFlow()

    private val _activeOperation = MutableStateFlow<TaskOperation?>(null)
    val activeOperation: StateFlow<TaskOperation?> = _activeOperation.asStateFlow()

    // Cached unfiltered task list to avoid re-fetching from Rust on filter changes
    private var _cachedAllTasks: List<WorkingSetItem> = emptyList()
    private var _tasksLoaded = false

    init {
        val appSettings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedStatus = appSettings.getString("app_settings_default_filter", null)
        _filter.value = TaskFilter(savedStatus, null, null, null)
        val savedSortName = appSettings.getString("app_settings_default_sort", SortField.URGENCY.name) ?: SortField.URGENCY.name
        _sortField.value = try { SortField.valueOf(savedSortName) } catch (e: Exception) { SortField.URGENCY }
        loadSyncConfig()
        loadTasks()
    }

    fun enterSelectionMode(operation: TaskOperation? = null) {
        _activeOperation.value = operation
    }

    fun toggleTaskSelection(uuid: String) {
        val current = _selectedUuids.value.toMutableSet()
        if (current.contains(uuid)) {
            current.remove(uuid)
        } else {
            current.add(uuid)
        }
        _selectedUuids.value = current
    }

    fun selectAll() {
        val allUuids = _tasks.value.map { it.task.uuid }.toSet()
        _selectedUuids.value = allUuids
    }

    fun clearSelection() {
        _selectedUuids.value = emptySet()
        _activeOperation.value = null
    }

    fun executeOperation() {
        val operation = _activeOperation.value ?: return
        val uuids = _selectedUuids.value.toList()
        if (uuids.isEmpty()) {
            clearSelection()
            return
        }

        viewModelScope.launch {
            try {
                when (operation) {
                    TaskOperation.COMPLETE -> {
                        uuids.forEach { repository.completeTask(it) }
                        _snackbarEvent.value = SnackbarEvent(
                            message = "${uuids.size} tasks completed",
                            actionLabel = "Undo",
                            taskUuids = uuids,
                            operation = TaskOperation.COMPLETE
                        )
                    }
                    TaskOperation.DELETE -> {
                        uuids.forEach { repository.deleteTask(it) }
                        _snackbarEvent.value = SnackbarEvent(
                            message = "${uuids.size} tasks deleted",
                            actionLabel = "Undo",
                            taskUuids = uuids,
                            operation = TaskOperation.DELETE
                        )
                    }
                    TaskOperation.UNCOMPLETE -> {
                        uuids.forEach { repository.uncompleteTask(it) }
                    }
                }
                loadTasks()
                clearSelection()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun batchUncomplete(uuids: List<String>) {
        viewModelScope.launch {
            try {
                uuids.forEach { repository.uncompleteTask(it) }
                loadTasks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearLocalData() {
        viewModelScope.launch {
            try {
                repository.clearLocalData()
                loadTasks()
                _snackbarEvent.value = SnackbarEvent(
                    message = "Local data cleared"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarEvent.value = SnackbarEvent(
                    message = "Failed to clear data: ${e.message}"
                )
            }
        }
    }

    private fun loadSyncConfig() {
        val url = prefs.getString("server_url", "") ?: ""
        val secret = prefs.getString("encryption_secret", "") ?: ""
        
        _serverUrl.value = url
        _encryptionSecret.value = secret
        
        var cid = prefs.getString("client_id", "") ?: ""
        if (cid.isEmpty()) {
            cid = UUID.randomUUID().toString()
            prefs.edit().putString("client_id", cid).apply()
        }
        _clientId.value = cid

        if (url.isNotEmpty() && secret.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    repository.configureSync(url, secret, cid)
                } catch (e: Exception) {
                    Log.w("TaskViewModel", "Failed to configure sync on init", e)
                }
            }
        }
    }

    fun saveSyncConfig(url: String, secret: String, clientId: String) {
        viewModelScope.launch {
            _serverUrl.value = url
            _encryptionSecret.value = secret
            _clientId.value = clientId
            
            prefs.edit()
                .putString("server_url", url)
                .putString("encryption_secret", secret)
                .putString("client_id", clientId)
                .apply()
                
            try {
                repository.configureSync(url, secret, clientId)
                _syncStatus.value = "Configuration saved"
                
                scheduleSyncIfConfigured()
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Failed to save configuration: ${e.message}"
            }
        }
    }

    private fun scheduleSyncIfConfigured() {
        if (_serverUrl.value.isNotEmpty() && _encryptionSecret.value.isNotEmpty()) {
            try {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()

                val syncWorkRequest = androidx.work.PeriodicWorkRequestBuilder<dev.rohans.taskwarrior.work.SyncWorker>(
                    15, java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    dev.rohans.taskwarrior.work.SyncWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                    syncWorkRequest
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    
    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Get working set for task IDs (needed for WorkingSetItem)
                val workingSet = repository.getWorkingSet()
                val idByUuid: Map<String, Long> = workingSet.associate { it.id.toLong() to it.task.uuid }.entries.associate { (id, uuid) -> uuid to id }

                // Get tasks sorted by current sort field
                val currentFilter = _filter.value
                val sortedTasks = repository.listTasksSorted(currentFilter, _sortField.value)

                // Wrap sorted TaskInfos as WorkingSetItems, using ID from working set (or 0 if not in working set)
                val sortedItems = sortedTasks.map { taskInfo ->
                    WorkingSetItem(id = (idByUuid[taskInfo.uuid] ?: 0L).toULong(), task = taskInfo)
                }
                _cachedAllTasks = sortedItems
                _tasksLoaded = true

                _availableProjects.value = sortedItems
                    .mapNotNull { it.task.project }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                _availableTags.value = sortedItems
                    .flatMap { it.task.tags }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                applyFilter()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applyFilter() {
        val currentFilter = _filter.value
        val allTasks = _cachedAllTasks

        _tasks.value = if (currentFilter.status == null &&
            currentFilter.project == null &&
            currentFilter.tag == null
        ) {
            allTasks.filter { !it.task.isWaiting }
        } else {
            allTasks.filter { item ->
                val task = item.task
                (currentFilter.status == null || task.status == currentFilter.status) &&
                (currentFilter.project == null || task.project == currentFilter.project) &&
                (currentFilter.tag == null || task.tags.contains(currentFilter.tag))
            }
        }
    }
    
    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
        applyFilter()
    }

    fun setSortField(sort: SortField) {
        _sortField.value = sort
        loadTasks()
    }
    
    fun createTask(description: String, onComplete: (TaskInfo) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val task = repository.createTask(description)
                loadTasks()
                onComplete(task)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun updateTask(uuid: String, updates: TaskUpdate, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateTask(uuid, updates)
                loadTasks()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun completeTask(uuid: String) {
        viewModelScope.launch {
            try {
                val completedTask = repository.completeTask(uuid)
                loadTasks()
                _snackbarEvent.value = SnackbarEvent(
                    message = "\"${completedTask.description}\" completed",
                    actionLabel = "Undo",
                    taskUuid = uuid
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startTask(uuid: String) {
        viewModelScope.launch {
            try {
                val task = repository.startTask(uuid)
                loadTasks()
                _snackbarEvent.value = SnackbarEvent(
                    message = "\"${task.description}\" started"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopTask(uuid: String) {
        viewModelScope.launch {
            try {
                val task = repository.stopTask(uuid)
                loadTasks()
                _snackbarEvent.value = SnackbarEvent(
                    message = "\"${task.description}\" stopped"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNext(uuid: String) {
        viewModelScope.launch {
            try {
                val task = repository.getTask(uuid) ?: return@launch
                val hasNext = task.tags.contains("next")
                val newTags = if (hasNext) task.tags.filter { it != "next" } else task.tags + "next"
                repository.updateTask(uuid, TaskUpdate(null, null, newTags, null, null, null, null))
                loadTasks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uncompleteTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.uncompleteTask(uuid)
                loadTasks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSnackbar() {
        _snackbarEvent.value = null
    }
    
    fun deleteTask(uuid: String) {
        viewModelScope.launch {
            try {
                repository.deleteTask(uuid)
                loadTasks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun sync(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _syncStatus.value = "Syncing..."
            try {
                val result = repository.sync()
                loadTasks()
                if (result.success) {
                    _syncStatus.value = "Sync successful"
                } else {
                    _syncStatus.value = result.message
                }
                onComplete(result.success)
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Sync error: ${e.message}"
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
      fun getTask(uuid: String, onResult: (TaskInfo?) -> Unit) {
        viewModelScope.launch {
            try {
                val cachedTask = _tasks.value.find { it.task.uuid == uuid }?.task
                if (cachedTask != null) {
                    onResult(cachedTask)
                } else {
                    val task = repository.getTask(uuid)
                    onResult(task)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }
    
    fun seedDebugTasks() {
        viewModelScope.launch {
            try {
                android.util.Log.d("TaskViewModel", "DEBUG: Starting seedDebugTasks")
                val task1 = repository.createTask("Fix login flow bug")
                android.util.Log.d("TaskViewModel", "DEBUG: Created task 1: ${task1.uuid}")
                try {
                    repository.updateTask(
                        task1.uuid,
                        TaskUpdate(
                            description = "Fix login flow bug",
                            project = "Development",
                            tags = listOf("backend"),
                            priority = "H",
                            due = null,
                            wait = null,
                            recur = null
                        )
                    )
                    android.util.Log.d("TaskViewModel", "DEBUG: Updated task 1")
                } catch (e: Exception) {
                    android.util.Log.d("TaskViewModel", "DEBUG: Failed to update task 1: ${e.message}")
                    e.printStackTrace()
                }
                
                val task2 = repository.createTask("Write API documentation")
                android.util.Log.d("TaskViewModel", "DEBUG: Created task 2: ${task2.uuid}")
                try {
                    repository.updateTask(
                        task2.uuid,
                        TaskUpdate(
                            description = "Write API documentation",
                            project = "Documentation",
                            tags = listOf("docs"),
                            priority = "M",
                            due = null,
                            wait = null,
                            recur = null
                        )
                    )
                    android.util.Log.d("TaskViewModel", "DEBUG: Updated task 2")
                } catch (e: Exception) {
                    android.util.Log.d("TaskViewModel", "DEBUG: Failed to update task 2: ${e.message}")
                    e.printStackTrace()
                }
                
                val task3 = repository.createTask("Review pull requests")
                android.util.Log.d("TaskViewModel", "DEBUG: Created task 3: ${task3.uuid}")
                try {
                    repository.updateTask(
                        task3.uuid,
                        TaskUpdate(
                            description = "Review pull requests",
                            project = "Development",
                            tags = listOf("review", "urgent"),
                            priority = "H",
                            due = null,
                            wait = null,
                            recur = null
                        )
                    )
                    android.util.Log.d("TaskViewModel", "DEBUG: Updated task 3")
                } catch (e: Exception) {
                    android.util.Log.d("TaskViewModel", "DEBUG: Failed to update task 3: ${e.message}")
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                android.util.Log.d("TaskViewModel", "DEBUG: specific seedDebugTasks error: ${e.message}")
                e.printStackTrace()
            } finally {
                android.util.Log.d("TaskViewModel", "DEBUG: seedDebugTasks finished, reloading tasks")
                loadTasks()
            }
        }
    }
}

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val taskUuid: String? = null,
    val taskUuids: List<String>? = null,
    val operation: TaskOperation? = null
)