package com.unitasks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.TaskList
import com.unitasks.app.domain.UnifiedGrouping
import com.unitasks.app.ui.theme.listAccentColor

/** One 48dp toolbar; detailed controls appear only when requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedControlsRow(
    lists: List<TaskList>,
    grouping: UnifiedGrouping,
    sortMode: SortMode,
    hiddenListIds: Set<String>,
    onSetGrouping: (UnifiedGrouping) -> Unit,
    onOpenSort: () -> Unit,
    onToggleListFilter: (String) -> Unit,
) {
    var groupingMenu by remember { mutableStateOf(false) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    val visibleCount = lists.count { it.id !in hiddenListIds }
    val filtered = visibleCount != lists.size
    val groupingLabel = when (grouping) {
        UnifiedGrouping.BY_LIST -> "By list"
        UnifiedGrouping.BY_DATE -> "By date"
        UnifiedGrouping.NONE -> "No grouping"
    }
    val sortLabel = when (sortMode) {
        SortMode.MY_ORDER -> "My order"
        SortMode.DATE -> "Date"
        SortMode.STARRED_RECENTLY -> "Starred recently"
        SortMode.TITLE -> "Title"
    }

    Row(
        modifier = Modifier.fillMaxWidth().testTag("unified_controls")
            .padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            TextButton(onClick = { groupingMenu = true }, modifier = Modifier.testTag("btn_grouping")) {
                Text(groupingLabel)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Change grouping", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = groupingMenu, onDismissRequest = { groupingMenu = false }) {
                listOf(
                    Triple(UnifiedGrouping.BY_LIST, "By list", "grouping_by_list"),
                    Triple(UnifiedGrouping.BY_DATE, "By date", "grouping_by_date"),
                    Triple(UnifiedGrouping.NONE, "No grouping", "grouping_none"),
                ).forEach { (mode, label, tag) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (grouping == mode) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = { onSetGrouping(mode); groupingMenu = false },
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        TextButton(onClick = { filterSheet = true }, modifier = Modifier.testTag("btn_list_filter")) {
            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (filtered) "  Lists $visibleCount/${lists.size}" else "  All lists",
                color = if (filtered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSort, modifier = Modifier.testTag("btn_all_tasks_sort")) {
            Icon(Icons.Default.SwapVert, contentDescription = "Sort all tasks: $sortLabel")
        }
    }
    HorizontalDivider()

    if (filterSheet) {
        ModalBottomSheet(
            onDismissRequest = { filterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lists to show", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { filterSheet = false }, modifier = Modifier.testTag("filter_done")) { Text("Done") }
                }
                Text(
                    "$visibleCount of ${lists.size} selected", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                TextButton(
                    enabled = filtered,
                    onClick = { lists.filter { it.id in hiddenListIds }.forEach { onToggleListFilter(it.id) } },
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("filter_show_all"),
                ) { Text("Show all lists") }
                LazyColumn {
                    items(lists, key = { it.id }) { list ->
                        val visible = list.id !in hiddenListIds
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("filter_list:${list.id}")
                                .toggleable(value = visible, role = Role.Checkbox, onValueChange = { onToggleListFilter(list.id) })
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(listAccentColor(list.id)))
                            Text(list.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Checkbox(checked = visible, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}
