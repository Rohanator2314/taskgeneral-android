package dev.rohans.taskwarrior.viewmodel

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

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    
    private val _tasks = MutableStateFlow<List<TaskInfo>>(emptyList())
    val tasks: StateFlow<List<TaskInfo>> = _tasks.asStateFlow()
    
    private val _filter = MutableStateFlow(TaskFilter(null, null, null))
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val taskList = if (_filter.value.status == null && 
                                   _filter.value.project == null && 
                                   _filter.value.tag == null) {
                    repository.listTasks()
                } else {
                    repository.listTasksFiltered(_filter.value)
                }
                _tasks.value = taskList
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
            try {
                val result = repository.sync()
                loadTasks()
                onComplete(result.success)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}
