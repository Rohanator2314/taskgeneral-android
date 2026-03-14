package dev.rohans.taskwarrior.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.rohans.taskwarrior.viewmodel.TaskViewModel
import uniffi.taskgeneral_core.SortField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    navController: NavController,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val sortOptions = listOf(
        SortField.URGENCY to "Urgency",
        SortField.DUE_DATE to "Due Date",
        SortField.PRIORITY to "Priority",
        SortField.ENTRY_DATE to "Newest",
        SortField.MODIFIED to "Modified",
        SortField.DESCRIPTION to "A-Z"
    )
    val savedSortName = prefs.getString("app_settings_default_sort", SortField.URGENCY.name) ?: SortField.URGENCY.name
    val savedSort = try { SortField.valueOf(savedSortName) } catch (e: Exception) { SortField.URGENCY }
    var defaultSort by remember { mutableStateOf(savedSort) }
    var isSortExpanded by remember { mutableStateOf(false) }

    val filterOptions = listOf(null to "All", "pending" to "Pending", "completed" to "Completed", "waiting" to "Waiting")
    val savedFilter = prefs.getString("app_settings_default_filter", null)
    var defaultFilter by remember { mutableStateOf(savedFilter) }
    var isFilterExpanded by remember { mutableStateOf(false) }

    var weightNext by remember { mutableStateOf(prefs.getFloat("app_settings_weight_next", 15.0f).toString()) }
    var weightDue by remember { mutableStateOf(prefs.getFloat("app_settings_weight_due", 12.0f).toString()) }
    var weightPriorityH by remember { mutableStateOf(prefs.getFloat("app_settings_weight_priority_h", 6.0f).toString()) }
    var weightActive by remember { mutableStateOf(prefs.getFloat("app_settings_weight_active", 4.0f).toString()) }
    var weightPriorityM by remember { mutableStateOf(prefs.getFloat("app_settings_weight_priority_m", 3.9f).toString()) }
    var weightAge by remember { mutableStateOf(prefs.getFloat("app_settings_weight_age", 2.0f).toString()) }
    var weightPriorityL by remember { mutableStateOf(prefs.getFloat("app_settings_weight_priority_l", 1.8f).toString()) }
    var weightWaiting by remember { mutableStateOf(prefs.getFloat("app_settings_weight_waiting", -3.0f).toString()) }

    var saveStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Display", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(
                expanded = isSortExpanded,
                onExpandedChange = { isSortExpanded = it }
            ) {
                OutlinedTextField(
                    value = sortOptions.firstOrNull { it.first == defaultSort }?.second ?: "Urgency",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default Sort") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSortExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isSortExpanded,
                    onDismissRequest = { isSortExpanded = false }
                ) {
                    sortOptions.forEach { (field, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                defaultSort = field
                                isSortExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = isFilterExpanded,
                onExpandedChange = { isFilterExpanded = it }
            ) {
                OutlinedTextField(
                    value = filterOptions.firstOrNull { it.first == defaultFilter }?.second ?: "All",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default Filter") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFilterExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isFilterExpanded,
                    onDismissRequest = { isFilterExpanded = false }
                ) {
                    filterOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                defaultFilter = value
                                isFilterExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            Text("Urgency Weights", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Adjust coefficients used in urgency calculation. Defaults match Taskwarrior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            listOf(
                "Next tag (+next)" to Triple(weightNext, { v: String -> weightNext = v }, "app_settings_weight_next"),
                "Due date" to Triple(weightDue, { v: String -> weightDue = v }, "app_settings_weight_due"),
                "Priority: High" to Triple(weightPriorityH, { v: String -> weightPriorityH = v }, "app_settings_weight_priority_h"),
                "Active (started)" to Triple(weightActive, { v: String -> weightActive = v }, "app_settings_weight_active"),
                "Priority: Medium" to Triple(weightPriorityM, { v: String -> weightPriorityM = v }, "app_settings_weight_priority_m"),
                "Age" to Triple(weightAge, { v: String -> weightAge = v }, "app_settings_weight_age"),
                "Priority: Low" to Triple(weightPriorityL, { v: String -> weightPriorityL = v }, "app_settings_weight_priority_l"),
                "Waiting penalty" to Triple(weightWaiting, { v: String -> weightWaiting = v }, "app_settings_weight_waiting")
            ).forEach { (label, triple) ->
                val (value, setter, _) = triple
                OutlinedTextField(
                    value = value,
                    onValueChange = setter,
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Button(
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    prefs.edit()
                        .putString("app_settings_default_sort", defaultSort.name)
                        .putString("app_settings_default_filter", defaultFilter)
                        .putFloat("app_settings_weight_next", weightNext.toFloatOrNull() ?: 15.0f)
                        .putFloat("app_settings_weight_due", weightDue.toFloatOrNull() ?: 12.0f)
                        .putFloat("app_settings_weight_priority_h", weightPriorityH.toFloatOrNull() ?: 6.0f)
                        .putFloat("app_settings_weight_active", weightActive.toFloatOrNull() ?: 4.0f)
                        .putFloat("app_settings_weight_priority_m", weightPriorityM.toFloatOrNull() ?: 3.9f)
                        .putFloat("app_settings_weight_age", weightAge.toFloatOrNull() ?: 2.0f)
                        .putFloat("app_settings_weight_priority_l", weightPriorityL.toFloatOrNull() ?: 1.8f)
                        .putFloat("app_settings_weight_waiting", weightWaiting.toFloatOrNull() ?: -3.0f)
                        .apply()
                    viewModel.setSortField(defaultSort)
                    saveStatus = "Settings saved"
                }
            ) {
                Text("Save Settings")
            }

            if (saveStatus != null) {
                Text(
                    text = saveStatus ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "TaskGeneral — Taskwarrior Android Client",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Core: TaskChampion 3.0.1",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
