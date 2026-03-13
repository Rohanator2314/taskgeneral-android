package dev.rohans.taskwarrior.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.rohans.taskwarrior.data.TaskRepository
import java.io.File

class TaskViewModelFactory(
    private val dataDir: File,
    private val prefs: SharedPreferences,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(TaskViewModel::class.java) ->
                TaskViewModel(TaskRepository(dataDir), prefs, context) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
