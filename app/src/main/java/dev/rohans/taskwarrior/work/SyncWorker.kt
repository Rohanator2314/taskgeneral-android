package dev.rohans.taskwarrior.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.rohans.taskwarrior.data.TaskRepository
import dev.rohans.taskwarrior.utils.EncryptedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "periodic_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting background sync")

        try {
            val prefs = EncryptedPreferencesHelper.getEncryptedSharedPreferences(applicationContext)
            
            val serverUrl = prefs.getString("server_url", "") ?: ""
            val encryptionSecret = prefs.getString("encryption_secret", "") ?: ""
            val clientId = prefs.getString("client_id", "") ?: ""

            if (serverUrl.isEmpty() || encryptionSecret.isEmpty()) {
                Log.w(TAG, "Sync not configured, skipping background sync")
                return@withContext Result.success()
            }

            val repository = TaskRepository(applicationContext.filesDir)
            repository.configureSync(serverUrl, encryptionSecret, clientId)
            
            val syncResult = repository.sync()

            if (syncResult.success) {
                Log.d(TAG, "Background sync successful")
                Result.success()
            } else {
                Log.e(TAG, "Background sync failed: ${syncResult.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background sync error: ${e.message}", e)
            Result.retry()
        }
    }
}
