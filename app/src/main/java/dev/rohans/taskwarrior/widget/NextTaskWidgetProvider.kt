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
import uniffi.taskgeneral_core.TaskUpdate
import java.io.File

class NextTaskWidgetProvider : AppWidgetProvider() {
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
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val dataDir = File(context.filesDir, "task_data")
                val repository = TaskRepository(dataDir)
                repository.completeTask(uuid)
                Toast.makeText(context, "Task completed", Toast.LENGTH_SHORT).show()
                updateAllWidgets(context)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to complete task: ${e.message}", Toast.LENGTH_SHORT).show()
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
        const val ACTION_COMPLETE_TASK = "dev.rohans.taskwarrior.widget.ACTION_COMPLETE_TASK"
        const val ACTION_START_TASK = "dev.rohans.taskwarrior.widget.ACTION_START_TASK"
        const val EXTRA_TASK_UUID = "task_uuid"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.Main).launch {
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

                    val views = if (nextTask != null) {
                        RemoteViews(context.packageName, R.layout.widget_next_task_1x1)
                    } else {
                        RemoteViews(context.packageName, R.layout.widget_next_task_1x1)
                    }

                    if (nextTask != null) {
                        views.setTextViewText(R.id.task_description, nextTask.description)
                    } else {
                        views.setTextViewText(R.id.task_description, context.getString(R.string.no_tasks))
                    }

                    if (nextTask != null) {
                        val completeIntent = Intent(context, NextTaskWidgetProvider::class.java).apply {
                            action = ACTION_COMPLETE_TASK
                            putExtra(EXTRA_TASK_UUID, nextTask.uuid)
                        }
                        val completePendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            completeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.complete_button, completePendingIntent)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    val views = RemoteViews(context.packageName, R.layout.widget_next_task_1x1)
                    views.setTextViewText(R.id.task_description, "Error loading task")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NextTaskWidgetProvider::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
