package dev.rohans.taskwarrior.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.rohans.taskwarrior.data.TaskRepository
import java.io.File

class TaskViewModelFactory(
    private val dataDir: File,
    private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(TaskRepository(dataDir), prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
