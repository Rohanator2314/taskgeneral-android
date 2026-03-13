package dev.rohans.taskwarrior

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.rohans.taskwarrior.navigation.Route
import dev.rohans.taskwarrior.ui.screens.SyncSettingsScreen
import dev.rohans.taskwarrior.ui.screens.TaskEditScreen
import dev.rohans.taskwarrior.ui.screens.TaskListScreen
import dev.rohans.taskwarrior.ui.theme.TaskGeneralTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskGeneralTheme {
                TaskGeneralApp()
            }
        }
    }
}

@Composable
fun TaskGeneralApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Route.TaskList.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Route.TaskList.route) {
            TaskListScreen(navController)
        }
        
        composable(
            route = Route.TaskEdit.route,
            arguments = listOf(navArgument("uuid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uuid = backStackEntry.arguments?.getString("uuid")
            TaskEditScreen(navController, uuid)
        }
        
        composable(Route.SyncSettings.route) {
            SyncSettingsScreen(navController)
        }
    }
}
