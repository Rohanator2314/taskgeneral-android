package dev.rohans.taskwarrior.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uniffi.taskgeneral_core.TaskFilter

@Composable
fun FilterRow(
    currentFilter: TaskFilter,
    availableProjects: List<String>,
    availableTags: List<String>,
    onFilterChange: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val statuses = listOf("pending", "completed", "waiting", "deleted")

        FilterChip(
            selected = currentFilter.status == null && currentFilter.project == null && currentFilter.tag == null,
            onClick = {
                onFilterChange(TaskFilter(null, null, null))
            },
            label = { Text("All") }
        )

        statuses.forEach { status ->
            FilterChip(
                selected = currentFilter.status == status,
                onClick = {
                    onFilterChange(currentFilter.copy(status = status))
                },
                label = { Text(status.replaceFirstChar { it.uppercase() }) }
            )
        }

        FilterDropdown(
            label = "Project",
            selectedItem = currentFilter.project,
            items = availableProjects,
            onItemSelected = { onFilterChange(currentFilter.copy(project = it)) }
        )

        FilterDropdown(
            label = "Tag",
            selectedItem = currentFilter.tag,
            items = availableTags,
            onItemSelected = { onFilterChange(currentFilter.copy(tag = it)) }
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selectedItem: String?,
    items: List<String>,
    onItemSelected: (String?) -> Unit
) {
    if (items.isEmpty() && selectedItem == null) return

    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedItem != null,
            onClick = { expanded = true },
            label = {
                Text(if (selectedItem != null) "$label: $selectedItem" else label)
            },
            trailingIcon = {
                if (selectedItem != null) {
                    IconButton(
                        onClick = { onItemSelected(null) },
                        modifier = Modifier.size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear $label filter"
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Show $label options",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onItemSelected(null)
                    expanded = false
                }
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
