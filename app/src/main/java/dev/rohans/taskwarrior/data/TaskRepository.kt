package dev.rohans.taskwarrior.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.taskgeneral_core.*
import java.io.File

class TaskRepository(dataDir: File) {
    private val taskManager: TaskManagerWrapper = createTaskManager(dataDir.absolutePath)

    suspend fun createTask(description: String): TaskInfo = withContext(Dispatchers.IO) {
        taskManager.createTask(description)
    }

    suspend fun getTask(uuid: String): TaskInfo? = withContext(Dispatchers.IO) {
        taskManager.getTask(uuid)
    }

    suspend fun listTasks(): List<TaskInfo> = withContext(Dispatchers.IO) {
        taskManager.listTasks()
    }

    suspend fun listTasksFiltered(filter: TaskFilter): List<TaskInfo> = withContext(Dispatchers.IO) {
        taskManager.listTasksFiltered(filter)
    }

    suspend fun getWorkingSet(): List<WorkingSetItem> = withContext(Dispatchers.IO) {
        taskManager.getWorkingSet()
    }

    suspend fun updateTask(uuid: String, updates: TaskUpdate): TaskInfo = withContext(Dispatchers.IO) {
        taskManager.updateTask(uuid, updates)
    }

    suspend fun completeTask(uuid: String): TaskInfo = withContext(Dispatchers.IO) {
        taskManager.completeTask(uuid)
    }

    suspend fun uncompleteTask(uuid: String): TaskInfo = withContext(Dispatchers.IO) {
        taskManager.uncompleteTask(uuid)
    }

    suspend fun deleteTask(uuid: String): Unit = withContext(Dispatchers.IO) {
        taskManager.deleteTask(uuid)
    }

    suspend fun configureSync(
        serverUrl: String,
        encryptionSecret: String,
        clientId: String
    ): Unit = withContext(Dispatchers.IO) {
        taskManager.configureSync(serverUrl, encryptionSecret, clientId)
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        taskManager.sync()
    }

    suspend fun clearLocalData(): Unit = withContext(Dispatchers.IO) {
        taskManager.clearLocalData()
    }
}
