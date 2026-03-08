package dev.rohans.taskwarrior.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import dev.rohans.taskwarrior.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskGeneralTopAppBar(navController: NavController) {
    TopAppBar(
        title = { Text("TaskGeneral") },
        actions = {
            IconButton(onClick = { 
                navController.navigate(Route.SyncSettings.route)
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }
    )
}
