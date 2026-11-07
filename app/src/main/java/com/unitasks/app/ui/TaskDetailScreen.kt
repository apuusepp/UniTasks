package com.unitasks.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.unitasks.app.data.TasksRepository
import com.unitasks.app.domain.Task
import com.unitasks.app.domain.TaskList
import com.unitasks.app.ui.components.DateTimeRepeatDialog
import com.unitasks.app.ui.components.formatDueChip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: String,
    repository: TasksRepository,
    onBack: () -> Unit,
    onNavigateToTask: (String) -> Unit,
) {
    var currentId by remember(taskId) { mutableStateOf(taskId) }
    val task by repository.observeTask(currentId).collectAsState(initial = null)
    val subtasks by repository.observeSubtasks(currentId).collectAsState(initial = emptyList())
    val lists by repository.observeLists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val current = task ?: return
    var title by remember(current.id) { mutableStateOf(current.title) }
    var notes by remember(current.id) { mutableStateOf(current.notes) }
    var newSubtaskTitle by remember { mutableStateOf("") }
    var showListMenu by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(current.title, current.notes) {
        if (title != current.title) title = current.title
        if (notes != current.notes) notes = current.notes
    }

    fun persistEdits() {
        scope.launch {
            repository.updateTask(current.id, title, notes, current.dueDate, current.dueTime, current.recurrence)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { persistEdits(); onBack() }, modifier = Modifier.testTag("detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {},
                actions = {
                    if (current.isSubtask) {
                        IconButton(onClick = { scope.launch { repository.outdent(current.id) } }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, contentDescription = "Unindent subtask")
                        }
                    } else if (subtasks.isEmpty()) {
                        IconButton(onClick = { scope.launch { repository.indent(current.id) } }) {
                            Icon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, contentDescription = "Make subtask")
                        }
                    }
                    IconButton(onClick = { scope.launch { repository.toggleStar(current.id) } }) {
                        if (current.starred) {
                            Icon(Icons.Filled.Star, contentDescription = "Remove from Starred", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.StarBorder, contentDescription = "Add to Starred")
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.deleteTask(current.id)
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("detail_delete"),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete task")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        scope.launch {
                            persistEdits()
                            repository.setCompleted(current.id, !current.completed)
                            onBack()
                        }
                    },
                    modifier = Modifier.padding(end = 8.dp).testTag("detail_toggle_complete"),
                ) {
                    Text(if (current.completed) "Mark uncompleted" else "Mark completed")
                }
            }
        },
    ) { padding ->
        val transparent = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Move-to-list selector (top-level tasks; subtasks inherit their parent's list)
            if (!current.isSubtask && lists.isNotEmpty()) {
                ListSelector(
                    lists = lists,
                    currentListId = current.listId,
                    expanded = showListMenu,
                    onExpandedChange = { showListMenu = it },
                    onSelectList = { targetListId ->
                        showListMenu = false
                        scope.launch {
                            persistEdits()
                            currentId = repository.moveTaskToList(current.id, targetListId)
                        }
                    },
                )
            }

            // Title
            TextField(
                value = title,
                onValueChange = {
                    title = it
                    scope.launch { repository.updateTask(current.id, it, notes, current.dueDate, current.dueTime, current.recurrence) }
                },
                placeholder = { Text("Enter title") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    textDecoration = if (current.completed) TextDecoration.LineThrough else TextDecoration.None,
                ),
                colors = transparent,
                modifier = Modifier.fillMaxWidth().testTag("detail_title"),
            )

            // Details
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Outlined.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        scope.launch { repository.updateTask(current.id, title, it, current.dueDate, current.dueTime, current.recurrence) }
                    },
                    placeholder = { Text("Add details") },
                    colors = transparent,
                    modifier = Modifier.fillMaxWidth().testTag("detail_notes"),
                )
            }

            // Date / time & repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDateDialog = true }
                    .padding(vertical = 12.dp)
                    .testTag("detail_date_row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(24.dp))
                Text(
                    text = current.dueDate?.let { formatDueChip(it, current.dueTime) } ?: "Add date/time",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current.dueDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (current.dueDate != null) {
                    IconButton(onClick = {
                        scope.launch { repository.updateTask(current.id, title, notes, null, null, null) }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear date")
                    }
                }
            }

            if (current.recurrence != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(24.dp))
                    Text(current.recurrence.describe(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Subtasks (only top-level tasks can have subtasks in Google Tasks)
            if (!current.isSubtask) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.SubdirectoryArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        subtasks.forEach { sub ->
                            SubtaskDetailRow(
                                subtask = sub,
                                onToggleComplete = { scope.launch { repository.setCompleted(sub.id, !sub.completed) } },
                                onOpen = { persistEdits(); onNavigateToTask(sub.id) },
                                onDelete = { scope.launch { repository.deleteTask(sub.id) } },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newSubtaskTitle,
                                onValueChange = { newSubtaskTitle = it },
                                placeholder = { Text("Add subtasks") },
                                singleLine = true,
                                colors = transparent,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (newSubtaskTitle.isNotBlank()) {
                                        val t = newSubtaskTitle
                                        newSubtaskTitle = ""
                                        scope.launch { repository.addTask(current.listId, t, parentId = current.id) }
                                    }
                                }),
                                modifier = Modifier.weight(1f).testTag("detail_add_subtask_input"),
                            )
                            if (newSubtaskTitle.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        val t = newSubtaskTitle
                                        newSubtaskTitle = ""
                                        scope.launch { repository.addTask(current.listId, t, parentId = current.id) }
                                    },
                                    modifier = Modifier.testTag("detail_add_subtask_save"),
                                ) { Text("Add") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDateDialog) {
        DateTimeRepeatDialog(
            initialDate = current.dueDate,
            initialTime = current.dueTime,
            initialRecurrence = current.recurrence,
            canRepeat = !current.isSubtask,
            onDismiss = { showDateDialog = false },
            onConfirm = { d, t, r ->
                scope.launch { repository.updateTask(current.id, title, notes, d, t, r) }
                showDateDialog = false
            },
        )
    }
}

@Composable
private fun ListSelector(
    lists: List<TaskList>,
    currentListId: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectList: (String) -> Unit,
) {
    Box {
        val listTitle = lists.firstOrNull { it.id == currentListId }?.title ?: ""
        TextButton(onClick = { onExpandedChange(true) }, modifier = Modifier.testTag("detail_list_selector")) {
            Text(listTitle, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Move to another list")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.title) },
                    onClick = { onSelectList(list.id) },
                )
            }
        }
    }
}

@Composable
private fun SubtaskDetailRow(
    subtask: Task,
    onToggleComplete: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleComplete) {
            if (subtask.completed) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "Uncomplete", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Complete")
            }
        }
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (subtask.completed) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Delete subtask")
        }
    }
}
