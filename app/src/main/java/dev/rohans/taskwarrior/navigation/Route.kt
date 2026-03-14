package dev.rohans.taskwarrior.navigation

sealed class Route(val route: String) {
    data object TaskList : Route("task_list")
    data object SyncSettings : Route("sync_settings")
    data object AppSettings : Route("app_settings")
    
    data object TaskEdit : Route("task_edit/{uuid}") {
        fun createRoute(uuid: String?): String {
            return if (uuid == null) {
                "task_edit/new"
            } else {
                "task_edit/$uuid"
            }
        }
    }
}
