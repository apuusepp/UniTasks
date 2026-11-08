package com.unitasks.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.unitasks.app.domain.RecurrenceRule
import com.unitasks.app.domain.TaskList
import java.time.LocalDate
import java.time.LocalTime

/**
 * Google Tasks-style "New task" bottom sheet. When opened from the unified "All tasks" or
 * "Starred" tab ([allowListSelection] = true), shows a list selector chip so the user can pick
 * which list the new task goes into.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskSheet(
    lists: List<TaskList>,
    initialListId: String,
    allowListSelection: Boolean,
    initialStarred: Boolean = false,
    parentId: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        listId: String,
        title: String,
        notes: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        starred: Boolean,
        recurrence: RecurrenceRule?,
    ) -> Unit,
    initialTitle: String = "",
    initialNotes: String = "",
    heading: String? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var showNotes by rememberSaveable { mutableStateOf(initialNotes.isNotBlank()) }
    var dueDate by rememberSaveable(stateSaver = Saver<LocalDate?, String>(
        save = { it?.toString() }, restore = { LocalDate.parse(it) },
    )) { mutableStateOf(null) }
    var dueTime by rememberSaveable(stateSaver = Saver<LocalTime?, String>(
        save = { it?.toString() }, restore = { LocalTime.parse(it) },
    )) { mutableStateOf(null) }
    var recurrence by rememberSaveable(stateSaver = Saver<RecurrenceRule?, String>(
        save = { it?.encode() }, restore = { RecurrenceRule.decode(it) },
    )) { mutableStateOf(null) }
    var starred by rememberSaveable { mutableStateOf(initialStarred) }
    var selectedListId by rememberSaveable { mutableStateOf(initialListId) }
    var showListMenu by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun submit() {
        if (title.isBlank() || isSaving || lists.isEmpty()) return
        onSave(selectedListId, title, notes, dueDate, dueTime, starred, recurrence)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            if (heading != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel shared task")
                    }
                }
            }
            if (allowListSelection && lists.isNotEmpty() && parentId == null) {
                Box {
                    val currentTitle = lists.firstOrNull { it.id == selectedListId }?.title ?: "List"
                    AssistChip(
                        onClick = { showListMenu = true },
                        label = { Text(currentTitle) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.testTag("add_list_picker"),
                    )
                    DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                        lists.forEach { list ->
                            DropdownMenuItem(
                                text = { Text(list.title) },
                                onClick = { selectedListId = list.id; showListMenu = false },
                            )
                        }
                    }
                }
            }
            val transparent = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = title,
                enabled = !isSaving,
                onValueChange = { title = it },
                placeholder = { Text(if (parentId == null) "New task" else "New subtask") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (showNotes) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = transparent,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("add_title"),
            )
            if (showNotes) {
                TextField(
                    value = notes,
                    enabled = !isSaving,
                    maxLines = 4,
                    onValueChange = { notes = it },
                    placeholder = { Text("Add details") },
                    colors = transparent,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().testTag("add_notes"),
                )
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (dueDate != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputChip(
                        selected = true,
                        onClick = { showDateDialog = true },
                        label = {
                            val base = formatDueChip(dueDate!!, dueTime)
                            Text(if (recurrence != null) "$base • ${recurrence!!.describe()}" else base)
                        },
                        trailingIcon = {
                            IconButton(onClick = { dueDate = null; dueTime = null; recurrence = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear date")
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showNotes = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Notes,
                        contentDescription = "Add details",
                        tint = if (showNotes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showDateDialog = true }) {
                    Icon(
                        Icons.Outlined.Event,
                        contentDescription = "Set date/time",
                        tint = if (dueDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { starred = !starred }) {
                    if (starred) {
                        Icon(Icons.Filled.Star, contentDescription = "Starred", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Outlined.StarBorder, contentDescription = "Add to Starred")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = ::submit,
                    enabled = title.isNotBlank() && !isSaving && lists.isNotEmpty(),
                    modifier = Modifier.testTag("add_save"),
                ) {
                    Text(if (isSaving) "Saving…" else "Save")
                }
            }
        }
    }

    if (showDateDialog) {
        DateTimeRepeatDialog(
            initialDate = dueDate,
            initialTime = dueTime,
            initialRecurrence = recurrence,
            canRepeat = parentId == null,
            onDismiss = { showDateDialog = false },
            onConfirm = { d, t, r ->
                dueDate = d
                dueTime = t
                recurrence = r
                showDateDialog = false
            },
        )
    }
}
