package dev.rohans.taskwarrior.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import dev.rohans.taskwarrior.R
import dev.rohans.taskwarrior.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uniffi.taskgeneral_core.TaskFilter
import uniffi.taskgeneral_core.SortField
import java.io.File

class NextTaskResizableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        if (action == ACTION_COMPLETE_TASK || action == ACTION_START_TASK) {
            val uuid = intent.getStringExtra(EXTRA_TASK_UUID) ?: return
            when (action) {
                ACTION_COMPLETE_TASK -> completeTask(context, uuid)
                ACTION_START_TASK -> startTask(context, uuid)
            }
        }
    }

    private fun completeTask(context: Context, uuid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataDir = File(context.filesDir, "task_data")
                val repository = TaskRepository(dataDir)
                repository.completeTask(uuid)
                updateAllWidgets(context)
            } catch (e: Exception) {
                android.util.Log.e("NextTaskResizableWidget", "Failed to complete task", e)
                Toast.makeText(context, "Failed to complete task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTask(context: Context, uuid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataDir = File(context.filesDir, "task_data")
                val repository = TaskRepository(dataDir)
                repository.startTask(uuid)
                updateAllWidgets(context)
            } catch (e: Exception) {
                android.util.Log.e("NextTaskResizableWidget", "Failed to start task", e)
                Toast.makeText(context, "Failed to start task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
        }
    }

    private fun startTask(context: Context, uuid: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val dataDir = File(context.filesDir, "task_data")
                val repository = TaskRepository(dataDir)
                repository.startTask(uuid)
                updateAllWidgets(context)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to start task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "dev.rohans.taskwarrior.widget.ACTION_COMPLETE_TASK_RESIZABLE"
        const val ACTION_START_TASK = "dev.rohans.taskwarrior.widget.ACTION_START_TASK_RESIZABLE"
        const val EXTRA_TASK_UUID = "task_uuid_resizable"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dataDir = File(context.filesDir, "task_data")
                    val repository = TaskRepository(dataDir)

                    val filter = TaskFilter(
                        status = "pending",
                        project = null,
                        tag = null,
                        sortBy = null
                    )
                    val tasks = repository.listTasksSorted(filter, SortField.URGENCY)
                    val nextTask = tasks.firstOrNull()

                    val views = RemoteViews(context.packageName, R.layout.widget_next_task_resizable)

                    if (nextTask != null) {
                        views.setTextViewText(R.id.task_description, nextTask.description)
                        views.setTextViewText(R.id.task_project, nextTask.project ?: "")

                        val completeIntent = Intent(context, NextTaskResizableWidgetProvider::class.java).apply {
                            action = ACTION_COMPLETE_TASK
                            putExtra(EXTRA_TASK_UUID, nextTask.uuid)
                        }
                        val completePendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId + 10000,
                            completeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.complete_button, completePendingIntent)

                        val startIntent = Intent(context, NextTaskResizableWidgetProvider::class.java).apply {
                            action = ACTION_START_TASK
                            putExtra(EXTRA_TASK_UUID, nextTask.uuid)
                        }
                        val startPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId + 20000,
                            startIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.start_button, startPendingIntent)
                    } else {
                        views.setTextViewText(R.id.task_description, context.getString(R.string.no_tasks))
                        views.setTextViewText(R.id.task_project, "")
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    android.util.Log.e("NextTaskResizableWidget", "Error loading task", e)
                    val views = RemoteViews(context.packageName, R.layout.widget_next_task_resizable)
                    views.setTextViewText(R.id.task_description, "Error: ${e.message}")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NextTaskResizableWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
