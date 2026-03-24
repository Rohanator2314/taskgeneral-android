package dev.rohans.taskwarrior.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import dev.rohans.taskwarrior.MainActivity
import dev.rohans.taskwarrior.R
import dev.rohans.taskwarrior.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class QuickAddFormWidgetProvider : AppWidgetProvider() {
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

        if (intent.action == ACTION_ADD_TASK) {
            val description = intent.getStringExtra(EXTRA_TASK_DESCRIPTION)
            if (!description.isNullOrBlank()) {
                addTask(context, description)
            }
        }
    }

    private fun addTask(context: Context, description: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataDir = File(context.filesDir, "task_data")
                val repository = TaskRepository(dataDir)
                repository.createTask(description)
                Toast.makeText(context, "Task added: $description", Toast.LENGTH_SHORT).show()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, QuickAddFormWidgetProvider::class.java)
                )
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_quick_add_1x2)
                    views.setTextViewText(R.id.task_description_input, "")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }

                NextTaskWidgetProvider.updateAllWidgets(context)
            } catch (e: Exception) {
                android.util.Log.e("QuickAddFormWidget", "Failed to add task", e)
                Toast.makeText(context, "Failed to add task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_ADD_TASK = "dev.rohans.taskwarrior.widget.ACTION_ADD_TASK_FORM"
        const val EXTRA_TASK_DESCRIPTION = "task_description"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add_1x2)

            val addIntent = Intent(context, QuickAddFormWidgetProvider::class.java).apply {
                action = ACTION_ADD_TASK
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_task_button, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
