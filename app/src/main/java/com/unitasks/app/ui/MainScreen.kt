package com.unitasks.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unitasks.app.data.AuthMode
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.SortMode
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskList
import com.unitasks.app.domain.TaskSection
import com.unitasks.app.domain.UnifiedGrouping
import com.unitasks.app.ui.components.AddTaskSheet
import com.unitasks.app.ui.components.DateTimeRepeatDialog
import com.unitasks.app.ui.components.TaskRow
import com.unitasks.app.ui.theme.listAccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    undoEvents: Flow<UndoAction> = emptyFlow(),
    today: LocalDate = LocalDate.now(),
    onSelectTab: (SelectedTab) -> Unit,
    onSetGrouping: (UnifiedGrouping) -> Unit,
    onToggleListFilter: (String) -> Unit,
    onSearchChange: (Boolean, String) -> Unit,
    onCreateList: (String) -> Unit,
    onRenameCurrentList: (String) -> Unit,
    onDeleteCurrentList: () -> Unit,
    onSetSort: (SortMode) -> Unit,
    onAddTask: (
        listId: String,
        title: String,
        notes: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        starred: Boolean,
        recurrence: RecurrenceRule?,
    ) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onToggleStar: (String) -> Unit,
    onUpdateTaskDateTime: (Task, LocalDate?, LocalTime?, RecurrenceRule?) -> Unit,
    onOpenTask: (String) -> Unit,
    onClearCompleted: (String?) -> Unit,
    onUndo: (UndoAction) -> Unit,
    onSyncNow: () -> Unit,
    onConnectGoogle: () -> Unit,
    onSignOut: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addSheetTargetListId by remember { mutableStateOf<String?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }
    var showDeleteListConfirm by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var editingDateForTask by remember { mutableStateOf<Task?>(null) }
    var completedExpanded by remember { mutableStateOf(false) }
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(undoEvents) {
        undoEvents.collect { action ->
            val result = snackbarHostState.showSnackbar(
                message = action.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo(action)
        }
    }

    Scaffold(
        topBar = {
            MainTopBar(
                state = state,
                onSearchChange = onSearchChange,
                onSyncNow = onSyncNow,
                onAccountClick = { showAccountDialog = true },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { showSortSheet = true }, modifier = Modifier.testTag("btn_sort")) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                    }
                    IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.testTag("btn_more")) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                floatingActionButton = {
                    val target = state.defaultTargetListId
                    FloatingActionButton(
                        onClick = {
                            if (target != null) addSheetTargetListId = target else showCreateListDialog = true
                        },
                        modifier = Modifier.testTag("fab_add_task"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add a task")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val pages = remember(state.lists) {
            listOf(SelectedTab.Starred, SelectedTab.AllTasks) + state.lists.map { SelectedTab.ListTab(it.id) }
        }
        val selectedIndex = pages.indexOf(state.selectedTab).let { if (it >= 0) it else 1.coerceAtMost(pages.lastIndex) }
        val pagerState = rememberPagerState(initialPage = selectedIndex) { pages.size }
        val currentSelectedTab by rememberUpdatedState(state.selectedTab)
        val currentOnSelectTab by rememberUpdatedState(onSelectTab)

        LaunchedEffect(selectedIndex) {
            if (pagerState.currentPage != selectedIndex) {
                pagerState.scrollToPage(selectedIndex)
            }
        }

        LaunchedEffect(pagerState.settledPage) {
            val pageTab = pages.getOrNull(pagerState.settledPage)
            if (pageTab != null && pageTab != currentSelectedTab) {
                currentOnSelectTab(pageTab)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TasksTabBar(
                lists = state.lists,
                selected = state.selectedTab,
                onSelect = onSelectTab,
                onNewList = { showCreateListDialog = true },
            )
            if (state.needsReauth) {
                BannerRow(
                    text = "Google session expired. Reconnect to continue syncing.",
                    actionLabel = "Reconnect",
                    onAction = onConnectGoogle,
                )
            } else if (state.syncError != null) {
                BannerRow(text = state.syncError, actionLabel = "Retry", onAction = onSyncNow)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("tasks_pager"),
            ) { pageIndex ->
                val pageTab = pages.getOrNull(pageIndex) ?: SelectedTab.AllTasks
                val (pageSections, pageCompleted) = if (pageTab == state.selectedTab) {
                    state.sections to state.completed
                } else {
                    state.contentForTab(pageTab, today)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (pageTab == SelectedTab.AllTasks) {
                        UnifiedControlsRow(
                            lists = state.lists,
                            grouping = state.unifiedGrouping,
                            sortMode = state.allTasksSort,
                            hiddenListIds = state.hiddenListIds,
                            onSetGrouping = onSetGrouping,
                            onOpenSort = { showSortSheet = true },
                            onToggleListFilter = onToggleListFilter,
                        )
                    }
                    val showBadgeOnRows = pageTab == SelectedTab.Starred ||
                        (pageTab == SelectedTab.AllTasks && state.unifiedGrouping != UnifiedGrouping.BY_LIST)

                    val allEmpty = pageSections.all { it.nodes.isEmpty() } && pageCompleted.isEmpty()
                    val noListsSelected = pageTab == SelectedTab.AllTasks && state.lists.isNotEmpty() &&
                        state.lists.all { it.id in state.hiddenListIds }
                    if (noListsSelected) {
                        EmptyTasksPlaceholder(
                            selectedTab = pageTab, isSearching = false,
                            onShowAllLists = { state.lists.forEach { onToggleListFilter(it.id) } },
                        )
                    } else if (allEmpty && (pageTab != SelectedTab.AllTasks || state.unifiedGrouping != UnifiedGrouping.BY_LIST)) {
                        EmptyTasksPlaceholder(selectedTab = pageTab, isSearching = state.searchQuery.isNotBlank())
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (pageTab == state.selectedTab) Modifier.testTag("tasks_list") else Modifier),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            pageSections.forEach { section ->
                                val isCollapsed = collapsedSections[section.key] == true
                                if (section.title != null) {
                                    item(key = "header:${section.key}") {
                                        SectionHeader(
                                            section = section,
                                            collapsed = isCollapsed,
                                            onToggleCollapse = { collapsedSections[section.key] = !isCollapsed },
                                            onAddInList = section.listId?.let { lid -> { addSheetTargetListId = lid } },
                                            onSelectList = section.listId?.let { lid -> { onSelectTab(SelectedTab.ListTab(lid)) } },
                                        )
                                    }
                                }
                                if (!isCollapsed) {
                                    if (section.nodes.isEmpty() && section.listId != null) {
                                        item(key = "empty:${section.key}") {
                                            Text(
                                                text = if (section.completedCount > 0) "All tasks completed (${section.completedCount})" else "No active tasks",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                    items(items = section.nodes, key = { "${section.key}:${it.task.id}" }) { node ->
                                        Column {
                                            TaskRow(
                                                task = node.task,
                                                listBadge = if (showBadgeOnRows) state.listTitles[node.task.listId] else null,
                                                today = today,
                                                onToggleComplete = { onToggleComplete(node.task) },
                                                onToggleStar = { onToggleStar(node.task.id) },
                                                onClick = { onOpenTask(node.task.id) },
                                                onDateClick = { editingDateForTask = node.task },
                                            )
                                            node.children.forEach { child ->
                                                TaskRow(
                                                    task = child,
                                                    listBadge = if (showBadgeOnRows) state.listTitles[child.listId] else null,
                                                    today = today,
                                                    onToggleComplete = { onToggleComplete(child) },
                                                    onToggleStar = { onToggleStar(child.id) },
                                                    onClick = { onOpenTask(child.id) },
                                                    onDateClick = { editingDateForTask = child },
                                                    indented = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (pageCompleted.isNotEmpty()) {
                                item(key = "completed_header") {
                                    Column {
                                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { completedExpanded = !completedExpanded }
                                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                                .testTag("completed_header"),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Completed (${pageCompleted.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Icon(
                                                imageVector = if (completedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                                if (completedExpanded) {
                                    val showCompletedBadge = pageTab !is SelectedTab.ListTab
                                    items(items = pageCompleted, key = { "done:${it.id}" }) { done ->
                                        TaskRow(
                                            task = done,
                                            listBadge = if (showCompletedBadge) state.listTitles[done.listId] else null,
                                            today = today,
                                            onToggleComplete = { onToggleComplete(done) },
                                            onToggleStar = { onToggleStar(done.id) },
                                            onClick = { onOpenTask(done.id) },
                                            onDateClick = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sheets & dialogs

    addSheetTargetListId?.let { targetListId ->
        AddTaskSheet(
            lists = state.lists,
            initialListId = targetListId,
            allowListSelection = state.selectedTab !is SelectedTab.ListTab,
            initialStarred = state.selectedTab == SelectedTab.Starred,
            onDismiss = { addSheetTargetListId = null },
            onSave = { listId, title, notes, date, time, starred, recurrence ->
                onAddTask(listId, title, notes, date, time, starred, recurrence)
                addSheetTargetListId = null
            },
        )
    }

    editingDateForTask?.let { task ->
        DateTimeRepeatDialog(
            initialDate = task.dueDate,
            initialTime = task.dueTime,
            initialRecurrence = task.recurrence,
            canRepeat = !task.isSubtask,
            onDismiss = { editingDateForTask = null },
            onConfirm = { d, t, r ->
                onUpdateTaskDateTime(task, d, t, r)
                editingDateForTask = null
            },
        )
    }

    if (showCreateListDialog) {
        TextPromptDialog(
            title = "Create new list",
            placeholder = "Enter list title",
            confirmLabel = "Done",
            onDismiss = { showCreateListDialog = false },
            onConfirm = {
                onCreateList(it)
                showCreateListDialog = false
            },
        )
    }

    if (showRenameListDialog) {
        val current = state.currentList
        if (current != null) {
            TextPromptDialog(
                title = "Rename list",
                initialValue = current.title,
                placeholder = "Enter list title",
                confirmLabel = "Done",
                onDismiss = { showRenameListDialog = false },
                onConfirm = {
                    onRenameCurrentList(it)
                    showRenameListDialog = false
                },
            )
        }
    }

    if (showDeleteListConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteListConfirm = false },
            title = { Text("Delete this list?") },
            text = { Text("All tasks in \"${state.currentList?.title.orEmpty()}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCurrentList()
                    showDeleteListConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteListConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showSortSheet) {
        val currentSort = when (state.selectedTab) {
            SelectedTab.Starred -> state.starredSort
            SelectedTab.AllTasks -> state.allTasksSort
            is SelectedTab.ListTab -> state.currentList?.sortMode ?: SortMode.MY_ORDER
        }
        val options = if (state.selectedTab == SelectedTab.Starred) {
            listOf(SortMode.STARRED_RECENTLY to "Starred recently", SortMode.DATE to "Date", SortMode.TITLE to "Title")
        } else {
            listOf(
                SortMode.MY_ORDER to "My order",
                SortMode.DATE to "Date",
                SortMode.STARRED_RECENTLY to "Starred recently",
                SortMode.TITLE to "Title",
            )
        }
        AlertDialog(
            onDismissRequest = { showSortSheet = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            trailingContent = { if (mode == currentSort) Icon(Icons.Default.Check, contentDescription = null) },
                            modifier = Modifier
                                .testTag("sort_option:${mode.name}")
                                .clickable {
                                    onSetSort(mode)
                                    showSortSheet = false
                                },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortSheet = false }) { Text("Close") }
            },
        )
    }

    if (showMoreSheet) {
        val list = state.currentList
        val hasCompleted = state.completed.isNotEmpty()
        AlertDialog(
            onDismissRequest = { showMoreSheet = false },
            title = { Text(list?.title ?: "Options") },
            text = {
                Column {
                    if (list != null) {
                        ListItem(
                            headlineContent = { Text("Rename list") },
                            modifier = Modifier.clickable {
                                showMoreSheet = false
                                showRenameListDialog = true
                            },
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Delete list",
                                    color = if (list.isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            supportingContent = if (list.isDefault) ({ Text("Default list can't be deleted") }) else null,
                            modifier = Modifier.clickable(enabled = !list.isDefault) {
                                showMoreSheet = false
                                showDeleteListConfirm = true
                            },
                        )
                        HorizontalDivider()
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                "Delete all completed tasks",
                                color = if (hasCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .testTag("menu_clear_completed")
                            .clickable(enabled = hasCompleted) {
                                showMoreSheet = false
                                onClearCompleted(list?.id)
                            },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreSheet = false }) { Text("Close") }
            },
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            state = state,
            onDismiss = { showAccountDialog = false },
            onSyncNow = { showAccountDialog = false; onSyncNow() },
            onConnectGoogle = { showAccountDialog = false; onConnectGoogle() },
            onSignOut = { showAccountDialog = false; onSignOut() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    state: MainUiState,
    onSearchChange: (Boolean, String) -> Unit,
    onSyncNow: () -> Unit,
    onAccountClick: () -> Unit,
) {
    if (state.isSearching) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchChange(false, "") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            },
            title = {
                TextField(
                    value = state.searchQuery,
                    onValueChange = { onSearchChange(true, it) },
                    placeholder = { Text("Search all tasks") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("search_input"),
                )
            },
            actions = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange(true, "") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
        )
    } else {
        TopAppBar(
            title = { Text("Tasks", fontWeight = FontWeight.Medium) },
            actions = {
                IconButton(onClick = { onSearchChange(true, "") }, modifier = Modifier.testTag("btn_search")) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                if (state.authMode == AuthMode.GOOGLE) {
                    IconButton(onClick = onSyncNow, enabled = !state.isSyncing) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                        }
                    }
                }
                IconButton(onClick = onAccountClick, modifier = Modifier.testTag("btn_account")) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTabBar(
    lists: List<TaskList>,
    selected: SelectedTab,
    onSelect: (SelectedTab) -> Unit,
    onNewList: () -> Unit,
) {
    val selectedIndex = when (selected) {
        SelectedTab.Starred -> 0
        SelectedTab.AllTasks -> 1
        is SelectedTab.ListTab -> lists.indexOfFirst { it.id == selected.listId }.let { if (it >= 0) it + 2 else 1 }
    }
    SecondaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 12.dp) {
        Tab(
            selected = selected == SelectedTab.Starred,
            onClick = { onSelect(SelectedTab.Starred) },
            icon = { Icon(Icons.Filled.Star, contentDescription = "Starred") },
            modifier = Modifier.testTag("tab_starred"),
        )
        Tab(
            selected = selected == SelectedTab.AllTasks,
            onClick = { onSelect(SelectedTab.AllTasks) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("All tasks")
                }
            },
            modifier = Modifier.testTag("tab_all_tasks"),
        )
        lists.forEach { list ->
            val isSelected = selected == SelectedTab.ListTab(list.id)
            Tab(
                selected = isSelected,
                onClick = { onSelect(SelectedTab.ListTab(list.id)) },
                text = { Text(list.title) },
                modifier = Modifier.testTag("tab_list:${list.id}"),
            )
        }
        Tab(
            selected = false,
            onClick = onNewList,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New list")
                }
            },
            modifier = Modifier.testTag("tab_new_list"),
        )
    }
}

@Composable
private fun SectionHeader(
    section: TaskSection,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onAddInList: (() -> Unit)?,
    onSelectList: (() -> Unit)?,
) {
    val activeCount = section.nodes.sumOf { 1 + it.children.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)
            .testTag("section:${section.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section.listId != null) {
            val accent = listAccentColor(section.listId)
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = section.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (section.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = if (onSelectList != null) Modifier.clickable(onClick = onSelectList) else Modifier,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = activeCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (onAddInList != null) {
            IconButton(
                onClick = onAddInList,
                modifier = Modifier.size(32.dp).testTag("add_in_list:${section.listId}"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task to ${section.title}", modifier = Modifier.size(18.dp))
            }
        }
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTasksPlaceholder(
    selectedTab: SelectedTab,
    isSearching: Boolean,
    onShowAllLists: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Text(
                text = when {
                    onShowAllLists != null -> "No lists selected"
                    isSearching -> "No matching tasks"
                    selectedTab == SelectedTab.Starred -> "No starred tasks"
                    else -> "No tasks yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when {
                    onShowAllLists != null -> "Choose which lists to show your tasks from"
                    isSearching -> "Try a different search term"
                    selectedTab == SelectedTab.Starred -> "Mark important tasks with a star so you can find them easily here"
                    else -> "Add your to-dos and keep track of them across all your lists"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onShowAllLists != null) {
                TextButton(onClick = onShowAllLists, modifier = Modifier.testTag("empty_show_all_lists")) {
                    Text("Show all lists")
                }
            }
        }
    }
}

@Composable
private fun BannerRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    initialValue: String = "",
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dialog_text_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("dialog_confirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
    onConnectGoogle: () -> Unit,
    onSignOut: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account & sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.authMode == AuthMode.GOOGLE) {
                    Text("Signed in with Google", style = MaterialTheme.typography.labelLarge)
                    state.accountEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val lastSync = state.lastSyncAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                    } ?: "Never"
                    Text("Last synced: $lastSync", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("On-device mode", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Tasks are stored locally on this device. Connect a Google account to sync with Google Tasks.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (state.authMode == AuthMode.GOOGLE) {
                TextButton(onClick = onSyncNow) { Text("Sync now") }
            } else {
                TextButton(onClick = onConnectGoogle) { Text("Sign in with Google") }
            }
        },
        dismissButton = {
            Row {
                if (state.authMode == AuthMode.GOOGLE) {
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
