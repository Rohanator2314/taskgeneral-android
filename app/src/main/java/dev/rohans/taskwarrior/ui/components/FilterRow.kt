package dev.rohans.taskwarrior.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
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
import uniffi.taskgeneral_core.SortField
import uniffi.taskgeneral_core.TaskFilter

@Composable
fun FilterRow(
    currentFilter: TaskFilter,
    availableProjects: List<String>,
    availableTags: List<String>,
    onFilterChange: (TaskFilter) -> Unit,
    currentSort: SortField,
    onSortChange: (SortField) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dropdown — shows "All" when null, otherwise shows selected status
        FilterDropdown(
            label = "Status",
            selectedItem = currentFilter.status,
            items = listOf("pending", "completed", "waiting", "deleted"),
            onItemSelected = { selected ->
                onFilterChange(TaskFilter(selected, currentFilter.project, currentFilter.tag, null))
            }
        )

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

        SortDropdown(
            currentSort = currentSort,
            onSortChange = onSortChange
        )
    }
}

@Composable
private fun SortDropdown(
    currentSort: SortField,
    onSortChange: (SortField) -> Unit
) {
    val sortOptions = listOf(
        SortField.URGENCY to "Urgency",
        SortField.DUE_DATE to "Due Date",
        SortField.PRIORITY to "Priority",
        SortField.ENTRY_DATE to "Newest",
        SortField.MODIFIED to "Modified",
        SortField.DESCRIPTION to "A-Z"
    )
    val currentLabel = sortOptions.firstOrNull { it.first == currentSort }?.second ?: "Sort"
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            shape = RectangleShape,
            selected = currentSort != SortField.URGENCY,
            onClick = { expanded = true },
            label = { Text("Sort: $currentLabel") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Show sort options",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sortOptions.forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSortChange(field)
                        expanded = false
                    }
                )
            }
        }
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
            shape = RectangleShape,
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
