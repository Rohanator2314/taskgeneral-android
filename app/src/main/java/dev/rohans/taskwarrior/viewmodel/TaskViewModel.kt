package dev.rohans.taskwarrior.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rohans.taskwarrior.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.taskgeneral_core.TaskFilter
import uniffi.taskgeneral_core.TaskInfo
import uniffi.taskgeneral_core.TaskUpdate
import uniffi.taskgeneral_core.WorkingSetItem
import java.util.UUID

class TaskViewModel(
    private val repository: TaskRepository,
    private val prefs: SharedPreferences
) : ViewModel() {
    
    private val _tasks = MutableStateFlow<List<WorkingSetItem>>(emptyList())
    val tasks: StateFlow<List<WorkingSetItem>> = _tasks.asStateFlow()
    
    private val _filter = MutableStateFlow(TaskFilter(null, null, null))
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _encryptionSecret = MutableStateFlow("")
    val encryptionSecret: StateFlow<String> = _encryptionSecret.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    init {
        loadSyncConfig()
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
    }

    fun saveSyncConfig(url: String, secret: String) {
        viewModelScope.launch {
            _serverUrl.value = url
            _encryptionSecret.value = secret
            
            prefs.edit()
                .putString("server_url", url)
                .putString("encryption_secret", secret)
                .apply()
                
            try {
                repository.configureSync(url, secret, _clientId.value)
                _syncStatus.value = "Configuration saved"
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Failed to save configuration: ${e.message}"
            }
        }
    }

    
    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allTasks = repository.getWorkingSet()
                val currentFilter = _filter.value
                
                val filteredTasks = if (currentFilter.status == null && 
                                   currentFilter.project == null && 
                                   currentFilter.tag == null) {
                    allTasks
                } else {
                    allTasks.filter { item ->
                        val task = item.task
                        (currentFilter.status == null || task.status == currentFilter.status) &&
                        (currentFilter.project == null || task.project == currentFilter.project) &&
                        (currentFilter.tag == null || task.tags.contains(currentFilter.tag))
                    }
                }
                _tasks.value = filteredTasks
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
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
                repository.completeTask(uuid)
                loadTasks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                if (_serverUrl.value.isNotEmpty() && _encryptionSecret.value.isNotEmpty()) {
                    repository.configureSync(_serverUrl.value, _encryptionSecret.value, _clientId.value)
                }

                val result = repository.sync()
                loadTasks()
                if (result.success) {
                    _syncStatus.value = "Sync successful"
                } else {
                    _syncStatus.value = "Sync failed"
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
                            priority = "H"
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
                            priority = "M"
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
                            priority = "H"
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
